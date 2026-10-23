package Inventory_Concurrent_Processing.service;

import Inventory_Concurrent_Processing.dto.ProcessingReportDto;
import Inventory_Concurrent_Processing.entity.InventoryItem;
import Inventory_Concurrent_Processing.entity.ProcessingError;
import Inventory_Concurrent_Processing.entity.ProcessingJob;
import Inventory_Concurrent_Processing.enums.JobStatus;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import Inventory_Concurrent_Processing.parser.CsvStreamingParser;
import Inventory_Concurrent_Processing.repository.InventoryItemRepository;
import Inventory_Concurrent_Processing.repository.ProcessingJobRepository;
import Inventory_Concurrent_Processing.service.batch.InventoryBatchWriter;
import Inventory_Concurrent_Processing.service.processing.ParallelProcessingService;
import Inventory_Concurrent_Processing.service.validation.CsvRecordValidator;
import Inventory_Concurrent_Processing.service.validation.DuplicateDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParallelProcessingServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private InventoryBatchWriter batchWriter;

    @Mock
    private ObjectProvider<DuplicateDetector> duplicateDetectorProvider;

    private ThreadPoolExecutor threadPool;
    private ParallelProcessingService parallelProcessingService;

    @BeforeEach
    void setUp() {
        threadPool = new ThreadPoolExecutor(
                4,
                8,
                10L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(50),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        CsvStreamingParser streamingParser = new CsvStreamingParser();
        CsvRecordValidator validator = new CsvRecordValidator();
        DuplicateDetector duplicateDetector = new DuplicateDetector(inventoryItemRepository);

        when(duplicateDetectorProvider.getObject()).thenReturn(duplicateDetector);

        parallelProcessingService = new ParallelProcessingService(
                streamingParser,
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
    @DisplayName("Should execute parallel processing with thread safety, correct record counts, and metrics")
    void testParallelProcessingPipeline() {
        Long jobId = 202L;
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .fileName("parallel_inventory.csv")
                .processingMode(ProcessingMode.PARALLEL)
                .status(JobStatus.PENDING)
                .build();

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(i -> i.getArgument(0));

        // Mock batch writer safely saving items
        when(batchWriter.saveInventoryBatchSafely(anyList(), any())).thenAnswer(invocation -> {
            List<?> items = invocation.getArgument(0);
            return items.size();
        });

        // Mock DB duplicate check
        when(inventoryItemRepository.existsByCodeAndBatchNumber(eq("MED003"), eq("B103"))).thenReturn(true);
        when(inventoryItemRepository.existsByCodeAndBatchNumber(eq("MED001"), eq("B101"))).thenReturn(false);
        when(inventoryItemRepository.existsByCodeAndBatchNumber(eq("MED002"), eq("B102"))).thenReturn(false);

        // CSV content:
        // Row 1: Valid
        // Row 2: In-file duplicate of Row 1 (MED001 + B101)
        // Row 3: Valid
        // Row 4: In-DB duplicate (MED003 + B103)
        // Row 5: Invalid (negative quantity)
        // Row 6: Invalid (price <= 0)
        // Row 7: Invalid (bad date)
        String csv = """
                code,name,batch_number,quantity,price,expiry_date
                MED001,Paracetamol 500mg,B101,100,12.50,2027-12-31
                MED001,Paracetamol 500mg,B101,50,12.50,2027-12-31
                MED002,Amoxicillin 250mg,B102,200,45.00,2026-06-30
                MED003,Ibuprofen 400mg,B103,150,25.00,2028-01-15
                MED004,Aspirin 100mg,B104,-10,5.00,2027-05-20
                MED005,Cough Syrup,B105,80,0.00,2026-11-10
                MED006,Vitamin C,B106,60,15.00,bad-date
                """;

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ProcessingReportDto report = parallelProcessingService.process(jobId, is);

        // Assertions matching baseline logic
        assertThat(report.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(report.getTotalRecords()).isEqualTo(7L);
        assertThat(report.getSuccessfulRecords()).isEqualTo(2L);
        assertThat(report.getDuplicateRecords()).isEqualTo(2L);
        assertThat(report.getFailedRecords()).isEqualTo(3L);

        // Verify metrics
        assertThat(report.getExecutionTimeMs()).isGreaterThanOrEqualTo(1L);
        assertThat(report.getThroughputRecordsPerSec()).isNotNull();
        assertThat(report.getPeakMemoryMb()).isNotNull();

        // Verify batch writer was invoked
        verify(batchWriter, atLeastOnce()).saveInventoryBatchSafely(anyList(), any());
        verify(batchWriter, atLeastOnce()).saveErrorBatch(anyList());
    }
}
