package Inventory_Concurrent_Processing.service.processing;

import Inventory_Concurrent_Processing.dto.ProcessingReportDto;
import Inventory_Concurrent_Processing.entity.InventoryItem;
import Inventory_Concurrent_Processing.entity.ProcessingError;
import Inventory_Concurrent_Processing.entity.ProcessingJob;
import Inventory_Concurrent_Processing.enums.JobStatus;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import Inventory_Concurrent_Processing.exception.CsvParseException;
import Inventory_Concurrent_Processing.model.csv.CsvRecord;
import Inventory_Concurrent_Processing.model.validation.ParsedInventoryRecord;
import Inventory_Concurrent_Processing.model.validation.ValidationResult;
import Inventory_Concurrent_Processing.parser.CsvStreamingParser;
import Inventory_Concurrent_Processing.repository.ProcessingJobRepository;
import Inventory_Concurrent_Processing.service.batch.InventoryBatchWriter;
import Inventory_Concurrent_Processing.service.validation.CsvRecordValidator;
import Inventory_Concurrent_Processing.service.validation.DuplicateDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

/**
 * <h2>Parallel Processing Pipeline Service</h2>
 *
 * <p>Implements multi-threaded CSV ingestion according to Phase 4 requirements:
 * <ul>
 *   <li><b>Task 4.1:</b> Bounded thread pool, controlled submission with backpressure, and {@link LongAdder} counters.</li>
 *   <li><b>Task 4.2:</b> Chunk-based task submission, parallel validation, duplicate detection, and batch persistence.</li>
 *   <li><b>Task 4.3:</b> Fault tolerance, partial failure isolation, and robust lifecycle management.</li>
 * </ul>
 */
@Service("parallelProcessingService")
public class ParallelProcessingService implements ProcessingEngine {

    private static final Logger log = LoggerFactory.getLogger(ParallelProcessingService.class);
    private static final int CHUNK_SIZE = 500;

    private final CsvStreamingParser csvStreamingParser;
    private final CsvRecordValidator csvRecordValidator;
    private final ObjectProvider<DuplicateDetector> duplicateDetectorProvider;
    private final InventoryBatchWriter batchWriter;
    private final ProcessingJobRepository processingJobRepository;
    private final ThreadPoolExecutor threadPool;

    public ParallelProcessingService(
            CsvStreamingParser csvStreamingParser,
            CsvRecordValidator csvRecordValidator,
            ObjectProvider<DuplicateDetector> duplicateDetectorProvider,
            InventoryBatchWriter batchWriter,
            ProcessingJobRepository processingJobRepository,
            @Qualifier("inventoryThreadPool") ThreadPoolExecutor threadPool) {
        this.csvStreamingParser = csvStreamingParser;
        this.csvRecordValidator = csvRecordValidator;
        this.duplicateDetectorProvider = duplicateDetectorProvider;
        this.batchWriter = batchWriter;
        this.processingJobRepository = processingJobRepository;
        this.threadPool = threadPool;
    }

    @Override
    public ProcessingReportDto process(Long jobId, InputStream inputStream) {
        log.info("Starting PARALLEL processing for job ID: {}", jobId);

        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with ID: " + jobId));

        job.setStatus(JobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        job.setProcessingMode(ProcessingMode.PARALLEL);
        processingJobRepository.save(job);

        // High-concurrency striped counters
        LongAdder totalCounter = new LongAdder();
        LongAdder successfulCounter = new LongAdder();
        LongAdder failedCounter = new LongAdder();
        LongAdder duplicateCounter = new LongAdder();

        // Prototype duplicate detector with thread-safe ConcurrentHashMap
        DuplicateDetector duplicateDetector = duplicateDetectorProvider.getObject();
        duplicateDetector.reset();

        long startMemory = getUsedMemoryBytes();
        long startTimeMs = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        List<CsvRecord> currentChunk = new ArrayList<>(CHUNK_SIZE);

        try (Stream<CsvRecord> recordStream = csvStreamingParser.stream(inputStream)) {
            var iterator = recordStream.iterator();

            while (iterator.hasNext()) {
                CsvRecord record = iterator.next();
                totalCounter.increment();
                currentChunk.add(record);

                if (currentChunk.size() >= CHUNK_SIZE) {
                    List<CsvRecord> chunkToSubmit = currentChunk;
                    currentChunk = new ArrayList<>(CHUNK_SIZE);
                    futures.add(submitChunkTask(chunkToSubmit, jobId, duplicateDetector,
                            successfulCounter, failedCounter, duplicateCounter));
                }
            }

            // Submit any remaining records
            if (!currentChunk.isEmpty()) {
                futures.add(submitChunkTask(currentChunk, jobId, duplicateDetector,
                        successfulCounter, failedCounter, duplicateCounter));
            }

            // Await completion of all submitted chunk tasks
            log.debug("Awaiting completion of {} parallel chunk tasks for job ID: {}", futures.size(), jobId);
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long endTimeMs = System.currentTimeMillis();
            long executionTimeMs = Math.max(1, endTimeMs - startTimeMs);
            long endMemory = getUsedMemoryBytes();
            long peakMemoryBytes = Math.max(startMemory, endMemory);

            long total = totalCounter.sum();
            long success = successfulCounter.sum();
            long failed = failedCounter.sum();
            long duplicates = duplicateCounter.sum();

            BigDecimal throughput = calculateThroughput(total, executionTimeMs);
            BigDecimal peakMemoryMb = toMb(peakMemoryBytes);

            job.setStatus(JobStatus.COMPLETED);
            job.setFinishedAt(LocalDateTime.now());
            job.setTotalRecords(total);
            job.setSuccessfulRecords(success);
            job.setFailedRecords(failed);
            job.setDuplicateRecords(duplicates);
            job.setExecutionTimeMs(executionTimeMs);
            job.setThroughputRecordsPerSec(throughput);
            job.setPeakMemoryMb(peakMemoryMb);

            processingJobRepository.save(job);
            log.info("Parallel processing COMPLETED for job ID {}: total={}, success={}, failed={}, duplicates={}, time={}ms, throughput={}/sec",
                    jobId, total, success, failed, duplicates, executionTimeMs, throughput);

            return buildReport(job);

        } catch (CsvParseException cpe) {
            log.error("CSV parse error in parallel processing for job ID: {}", jobId, cpe);
            return handleJobFailure(job, cpe.getMessage(), totalCounter.sum(), successfulCounter.sum(),
                    failedCounter.sum(), duplicateCounter.sum(), startTimeMs);
        } catch (Exception e) {
            log.error("Unexpected error in parallel processing for job ID: {}", jobId, e);
            return handleJobFailure(job, e.getMessage(), totalCounter.sum(), successfulCounter.sum(),
                    failedCounter.sum(), duplicateCounter.sum(), startTimeMs);
        }
    }

    /**
     * Submits a single chunk of records to the bounded thread pool.
     */
    private CompletableFuture<Void> submitChunkTask(
            List<CsvRecord> chunk,
            Long jobId,
            DuplicateDetector duplicateDetector,
            LongAdder successfulCounter,
            LongAdder failedCounter,
            LongAdder duplicateCounter) {

        return CompletableFuture.runAsync(() -> {
            List<InventoryItem> validBatch = new ArrayList<>(chunk.size());
            List<ProcessingError> errorBatch = new ArrayList<>(chunk.size());

            for (CsvRecord record : chunk) {
                // 1. Validate fields
                ValidationResult validationResult = csvRecordValidator.validate(record);

                if (!validationResult.isValid()) {
                    failedCounter.increment();
                    errorBatch.add(ProcessingError.builder()
                            .jobId(jobId)
                            .rowNumber(record.getRowNumber())
                            .errorType("VALIDATION_ERROR")
                            .rawRecord(record.getRawLine())
                            .errorMessage(validationResult.buildErrorMessage())
                            .build());
                } else {
                    // 2. Thread-safe duplicate detection
                    ParsedInventoryRecord parsed = validationResult.getParsedRecord();
                    ValidationResult duplicateCheck = duplicateDetector.checkDuplicate(record, parsed);

                    if (duplicateCheck.isDuplicate()) {
                        duplicateCounter.increment();
                        errorBatch.add(ProcessingError.builder()
                                .jobId(jobId)
                                .rowNumber(record.getRowNumber())
                                .errorType("DUPLICATE")
                                .rawRecord(record.getRawLine())
                                .errorMessage(duplicateCheck.buildErrorMessage())
                                .build());
                    } else {
                        validBatch.add(InventoryItem.builder()
                                .code(parsed.getCode())
                                .name(parsed.getName())
                                .batchNumber(parsed.getBatchNumber())
                                .quantity(parsed.getQuantity())
                                .price(parsed.getPrice())
                                .expiryDate(parsed.getExpiryDate())
                                .sourceJobId(jobId)
                                .build());
                    }
                }
            }

            // 3. Persist valid batch safely with fallback if constraint collision occurs
            if (!validBatch.isEmpty()) {
                List<ProcessingError> collisionErrors = new ArrayList<>();
                int savedCount = batchWriter.saveInventoryBatchSafely(validBatch, collisionErrors);
                successfulCounter.add(savedCount);

                int collisions = validBatch.size() - savedCount;
                if (collisions > 0) {
                    duplicateCounter.add(collisions);
                    errorBatch.addAll(collisionErrors);
                }
            }

            // 4. Persist chunk error batch
            if (!errorBatch.isEmpty()) {
                batchWriter.saveErrorBatch(errorBatch);
            }

        }, threadPool);
    }

    private ProcessingReportDto handleJobFailure(
            ProcessingJob job,
            String errorMessage,
            long total,
            long success,
            long failed,
            long duplicates,
            long startTimeMs) {
        long executionTimeMs = System.currentTimeMillis() - startTimeMs;
        job.setStatus(JobStatus.FAILED);
        job.setFinishedAt(LocalDateTime.now());
        job.setTotalRecords(total);
        job.setSuccessfulRecords(success);
        job.setFailedRecords(failed);
        job.setDuplicateRecords(duplicates);
        job.setExecutionTimeMs(executionTimeMs);
        job.setErrorMessage(errorMessage);
        processingJobRepository.save(job);

        return buildReport(job);
    }

    private ProcessingReportDto buildReport(ProcessingJob job) {
        return ProcessingReportDto.builder()
                .jobId(job.getId())
                .fileName(job.getFileName())
                .processingMode(job.getProcessingMode())
                .status(job.getStatus())
                .startedAt(job.getStartedAt())
                .finishedAt(job.getFinishedAt())
                .totalRecords(job.getTotalRecords())
                .successfulRecords(job.getSuccessfulRecords())
                .failedRecords(job.getFailedRecords())
                .duplicateRecords(job.getDuplicateRecords())
                .executionTimeMs(job.getExecutionTimeMs())
                .throughputRecordsPerSec(job.getThroughputRecordsPerSec())
                .peakMemoryMb(job.getPeakMemoryMb())
                .errorMessage(job.getErrorMessage())
                .build();
    }

    private static BigDecimal calculateThroughput(long totalRecords, long durationMs) {
        if (durationMs <= 0 || totalRecords <= 0) {
            return BigDecimal.ZERO;
        }
        double seconds = durationMs / 1000.0;
        return BigDecimal.valueOf(totalRecords / seconds).setScale(2, RoundingMode.HALF_UP);
    }

    private static long getUsedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static BigDecimal toMb(long bytes) {
        return BigDecimal.valueOf(bytes / (1024.0 * 1024.0)).setScale(2, RoundingMode.HALF_UP);
    }
}
