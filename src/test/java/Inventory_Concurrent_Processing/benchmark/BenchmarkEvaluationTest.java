package Inventory_Concurrent_Processing.benchmark;

import Inventory_Concurrent_Processing.dto.ProcessingReportDto;
import Inventory_Concurrent_Processing.entity.ProcessingJob;
import Inventory_Concurrent_Processing.enums.JobStatus;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import Inventory_Concurrent_Processing.parser.CsvStreamingParser;
import Inventory_Concurrent_Processing.repository.InventoryItemRepository;
import Inventory_Concurrent_Processing.repository.ProcessingJobRepository;
import Inventory_Concurrent_Processing.service.batch.InventoryBatchWriter;
import Inventory_Concurrent_Processing.service.processing.ParallelProcessingService;
import Inventory_Concurrent_Processing.service.processing.SequentialProcessingService;
import Inventory_Concurrent_Processing.service.validation.CsvRecordValidator;
import Inventory_Concurrent_Processing.service.validation.DuplicateDetector;
import Inventory_Concurrent_Processing.util.InventoryDatasetGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * <h2>Benchmark Evaluation Harness</h2>
 *
 * Runs both Sequential Baseline and Parallel implementations on the identical
 * 10,000+ record dataset to measure and compare:
 * <ul>
 *   <li>Total execution duration (ms)</li>
 *   <li>Throughput (records per second)</li>
 *   <li>Peak memory usage (MB)</li>
 *   <li>Output correctness between implementations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BenchmarkEvaluationTest {

    private static final int DATASET_SIZE = 10_000;
    private static final File BENCHMARK_FILE = new File("src/main/resources/benchmark/inventory_10000.csv");

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private InventoryBatchWriter batchWriter;

    @Mock
    private ObjectProvider<DuplicateDetector> duplicateDetectorProvider;

    private ThreadPoolExecutor threadPool;
    private SequentialProcessingService sequentialService;
    private ParallelProcessingService parallelService;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure 10k dataset is present
        if (!BENCHMARK_FILE.exists()) {
            InventoryDatasetGenerator.generateCsv(DATASET_SIZE, BENCHMARK_FILE);
        }

        threadPool = new ThreadPoolExecutor(
                8,
                16,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(50),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        CsvStreamingParser parser = new CsvStreamingParser();
        CsvRecordValidator validator = new CsvRecordValidator();

        // Fresh duplicate detector per call
        when(duplicateDetectorProvider.getObject()).thenAnswer(inv -> new DuplicateDetector(inventoryItemRepository));

        // Mock batch writer saves
        doNothing().when(batchWriter).saveInventoryBatch(anyList());
        doNothing().when(batchWriter).saveErrorBatch(anyList());
        when(batchWriter.saveInventoryBatchSafely(anyList(), any())).thenAnswer(inv -> {
            List<?> items = inv.getArgument(0);
            return items.size();
        });

        sequentialService = new SequentialProcessingService(
                parser,
                validator,
                duplicateDetectorProvider,
                batchWriter,
                processingJobRepository
        );

        parallelService = new ParallelProcessingService(
                parser,
                validator,
                duplicateDetectorProvider,
                batchWriter,
                processingJobRepository,
                threadPool
        );
    }

    @AfterEach
    void tearDown() {
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Evaluate and compare Sequential vs Parallel on 10,000 records")
    void runBenchmarkEvaluation() throws IOException {
        Long seqJobId = 1L;
        ProcessingJob seqJob = ProcessingJob.builder()
                .id(seqJobId)
                .fileName("inventory_10000.csv")
                .processingMode(ProcessingMode.SEQUENTIAL)
                .status(JobStatus.PENDING)
                .build();

        Long parJobId = 2L;
        ProcessingJob parJob = ProcessingJob.builder()
                .id(parJobId)
                .fileName("inventory_10000.csv")
                .processingMode(ProcessingMode.PARALLEL)
                .status(JobStatus.PENDING)
                .build();

        when(processingJobRepository.findById(seqJobId)).thenReturn(Optional.of(seqJob));
        when(processingJobRepository.findById(parJobId)).thenReturn(Optional.of(parJob));
        when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Run Sequential Baseline
        ProcessingReportDto seqReport;
        try (InputStream is = new FileInputStream(BENCHMARK_FILE)) {
            seqReport = sequentialService.process(seqJobId, is);
        }

        // 2. Run Parallel Implementation
        ProcessingReportDto parReport;
        try (InputStream is = new FileInputStream(BENCHMARK_FILE)) {
            parReport = parallelService.process(parJobId, is);
        }

        // 3. Verify Output Correctness
        assertThat(seqReport.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(parReport.getStatus()).isEqualTo(JobStatus.COMPLETED);

        assertThat(parReport.getTotalRecords()).isEqualTo(seqReport.getTotalRecords());
        assertThat(parReport.getSuccessfulRecords()).isEqualTo(seqReport.getSuccessfulRecords());
        assertThat(parReport.getFailedRecords()).isEqualTo(seqReport.getFailedRecords());
        assertThat(parReport.getDuplicateRecords()).isEqualTo(seqReport.getDuplicateRecords());

        // 4. Calculate Speedup
        double speedup = (double) seqReport.getExecutionTimeMs() / Math.max(1, parReport.getExecutionTimeMs());

        // Print Structured Performance Report
        System.out.println("===============================================================================");
        System.out.println("             PERFORMANCE EVALUATION REPORT (10,000 RECORDS)                   ");
        System.out.println("===============================================================================");
        System.out.printf("%-30s : %s%n", "Dataset File", BENCHMARK_FILE.getName());
        System.out.printf("%-30s : %,d%n", "Total Records Processed", seqReport.getTotalRecords());
        System.out.printf("%-30s : %,d%n", "Successful (Valid) Records", seqReport.getSuccessfulRecords());
        System.out.printf("%-30s : %,d%n", "Validation Failed Records", seqReport.getFailedRecords());
        System.out.printf("%-30s : %,d%n", "Duplicate Records", seqReport.getDuplicateRecords());
        System.out.println("-------------------------------------------------------------------------------");
        System.out.printf("%-30s : %,d ms%n", "Sequential Execution Time", seqReport.getExecutionTimeMs());
        System.out.printf("%-30s : %,d ms%n", "Parallel Execution Time", parReport.getExecutionTimeMs());
        System.out.printf("%-30s : %.2fx%n", "Parallel Speedup Factor", speedup);
        System.out.println("-------------------------------------------------------------------------------");
        System.out.printf("%-30s : %s rec/sec%n", "Sequential Throughput", seqReport.getThroughputRecordsPerSec());
        System.out.printf("%-30s : %s rec/sec%n", "Parallel Throughput", parReport.getThroughputRecordsPerSec());
        System.out.println("-------------------------------------------------------------------------------");
        System.out.printf("%-30s : %s MB%n", "Sequential Peak Memory", seqReport.getPeakMemoryMb());
        System.out.printf("%-30s : %s MB%n", "Parallel Peak Memory", parReport.getPeakMemoryMb());
        System.out.printf("%-30s : %s%n", "Output Correctness", "100% IDENTICAL (Valid, Failed, Duplicates)");
        System.out.println("===============================================================================");
    }
}
