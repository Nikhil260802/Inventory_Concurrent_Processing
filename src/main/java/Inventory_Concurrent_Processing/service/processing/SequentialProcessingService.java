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
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * <h2>Sequential Processing Baseline Service</h2>
 *
 * <p>Implements the single-threaded baseline processing pipeline according to:
 * <ul>
 *   <li>Task 3.1: Read stream -> Validate -> Detect duplicates -> Batch DB insert -> Save errors.</li>
 *   <li>Task 3.2: Record execution time, throughput, record counts, and peak memory usage.</li>
 * </ul>
 */
@Service("sequentialProcessingService")
public class SequentialProcessingService implements ProcessingEngine {

    private static final Logger log = LoggerFactory.getLogger(SequentialProcessingService.class);
    private static final int BATCH_SIZE = 500;

    private final CsvStreamingParser csvStreamingParser;
    private final CsvRecordValidator csvRecordValidator;
    private final ObjectProvider<DuplicateDetector> duplicateDetectorProvider;
    private final InventoryBatchWriter batchWriter;
    private final ProcessingJobRepository processingJobRepository;

    public SequentialProcessingService(
            CsvStreamingParser csvStreamingParser,
            CsvRecordValidator csvRecordValidator,
            ObjectProvider<DuplicateDetector> duplicateDetectorProvider,
            InventoryBatchWriter batchWriter,
            ProcessingJobRepository processingJobRepository) {
        this.csvStreamingParser = csvStreamingParser;
        this.csvRecordValidator = csvRecordValidator;
        this.duplicateDetectorProvider = duplicateDetectorProvider;
        this.batchWriter = batchWriter;
        this.processingJobRepository = processingJobRepository;
    }

    @Override
    public ProcessingReportDto process(Long jobId, InputStream inputStream) {
        log.info("Starting SEQUENTIAL processing for job ID: {}", jobId);

        ProcessingJob job = processingJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found with ID: " + jobId));

        job.setStatus(JobStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        job.setProcessingMode(ProcessingMode.SEQUENTIAL);
        processingJobRepository.save(job);

        // Fresh duplicate detector per job
        DuplicateDetector duplicateDetector = duplicateDetectorProvider.getObject();
        duplicateDetector.reset();

        long totalRecords = 0L;
        long successfulRecords = 0L;
        long failedRecords = 0L;
        long duplicateRecords = 0L;

        List<InventoryItem> validBatch = new ArrayList<>(BATCH_SIZE);
        List<ProcessingError> errorBatch = new ArrayList<>(BATCH_SIZE);

        long startMemory = getUsedMemoryBytes();
        long peakMemoryBytes = startMemory;
        long startTimeMs = System.currentTimeMillis();

        try (Stream<CsvRecord> recordStream = csvStreamingParser.stream(inputStream)) {
            var iterator = recordStream.iterator();

            while (iterator.hasNext()) {
                CsvRecord record = iterator.next();
                totalRecords++;

                // 1. Validate mandatory fields, formats, numbers, dates
                ValidationResult validationResult = csvRecordValidator.validate(record);

                if (!validationResult.isValid()) {
                    failedRecords++;
                    errorBatch.add(ProcessingError.builder()
                            .jobId(jobId)
                            .rowNumber(record.getRowNumber())
                            .errorType("VALIDATION_ERROR")
                            .rawRecord(record.getRawLine())
                            .errorMessage(validationResult.buildErrorMessage())
                            .build());
                } else {
                    // 2. Duplicate detection (in-file & DB)
                    ParsedInventoryRecord parsed = validationResult.getParsedRecord();
                    ValidationResult duplicateCheck = duplicateDetector.checkDuplicate(record, parsed);

                    if (duplicateCheck.isDuplicate()) {
                        duplicateRecords++;
                        errorBatch.add(ProcessingError.builder()
                                .jobId(jobId)
                                .rowNumber(record.getRowNumber())
                                .errorType("DUPLICATE")
                                .rawRecord(record.getRawLine())
                                .errorMessage(duplicateCheck.buildErrorMessage())
                                .build());
                    } else {
                        // Valid & non-duplicate -> prepare entity
                        successfulRecords++;
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

                // 3. Flush batches when full
                if (validBatch.size() >= BATCH_SIZE) {
                    batchWriter.saveInventoryBatch(new ArrayList<>(validBatch));
                    validBatch.clear();
                    peakMemoryBytes = Math.max(peakMemoryBytes, getUsedMemoryBytes());
                }

                if (errorBatch.size() >= BATCH_SIZE) {
                    batchWriter.saveErrorBatch(new ArrayList<>(errorBatch));
                    errorBatch.clear();
                    peakMemoryBytes = Math.max(peakMemoryBytes, getUsedMemoryBytes());
                }
            }

            // Flush remaining items
            if (!validBatch.isEmpty()) {
                batchWriter.saveInventoryBatch(new ArrayList<>(validBatch));
                validBatch.clear();
            }
            if (!errorBatch.isEmpty()) {
                batchWriter.saveErrorBatch(new ArrayList<>(errorBatch));
                errorBatch.clear();
            }

            peakMemoryBytes = Math.max(peakMemoryBytes, getUsedMemoryBytes());
            long endTimeMs = System.currentTimeMillis();
            long executionTimeMs = Math.max(1, endTimeMs - startTimeMs);

            BigDecimal throughput = calculateThroughput(totalRecords, executionTimeMs);
            BigDecimal peakMemoryMb = toMb(peakMemoryBytes);

            job.setStatus(JobStatus.COMPLETED);
            job.setFinishedAt(LocalDateTime.now());
            job.setTotalRecords(totalRecords);
            job.setSuccessfulRecords(successfulRecords);
            job.setFailedRecords(failedRecords);
            job.setDuplicateRecords(duplicateRecords);
            job.setExecutionTimeMs(executionTimeMs);
            job.setThroughputRecordsPerSec(throughput);
            job.setPeakMemoryMb(peakMemoryMb);

            processingJobRepository.save(job);
            log.info("Sequential processing COMPLETED for job ID {}: total={}, success={}, failed={}, duplicates={}, time={}ms, throughput={}/sec",
                    jobId, totalRecords, successfulRecords, failedRecords, duplicateRecords, executionTimeMs, throughput);

            return buildReport(job);

        } catch (CsvParseException cpe) {
            log.error("CSV parse error in sequential processing for job ID: {}", jobId, cpe);
            return handleJobFailure(job, cpe.getMessage(), totalRecords, successfulRecords, failedRecords, duplicateRecords, startTimeMs);
        } catch (Exception e) {
            log.error("Unexpected error in sequential processing for job ID: {}", jobId, e);
            return handleJobFailure(job, e.getMessage(), totalRecords, successfulRecords, failedRecords, duplicateRecords, startTimeMs);
        }
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
