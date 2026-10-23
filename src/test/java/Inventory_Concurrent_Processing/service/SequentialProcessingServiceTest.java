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
import Inventory_Concurrent_Processing.service.processing.SequentialProcessingService;
import Inventory_Concurrent_Processing.service.validation.CsvRecordValidator;
import Inventory_Concurrent_Processing.service.validation.DuplicateDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SequentialProcessingServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private InventoryBatchWriter batchWriter;

    @Mock
    private ObjectProvider<DuplicateDetector> duplicateDetectorProvider;

    private SequentialProcessingService sequentialProcessingService;

    @BeforeEach
    void setUp() {
        CsvStreamingParser streamingParser = new CsvStreamingParser();
        CsvRecordValidator validator = new CsvRecordValidator();
        DuplicateDetector duplicateDetector = new DuplicateDetector(inventoryItemRepository);

        when(duplicateDetectorProvider.getObject()).thenReturn(duplicateDetector);

        sequentialProcessingService = new SequentialProcessingService(
                streamingParser,
                validator,
                duplicateDetectorProvider,
                batchWriter,
                processingJobRepository
        );
    }

    @Test
    @DisplayName("Should successfully process valid CSV, detect duplicates, record errors, and compute metrics")
    void testSequentialProcessingBaseline() {
        Long jobId = 101L;
        ProcessingJob job = ProcessingJob.builder()
                .id(jobId)
                .fileName("test_inventory.csv")
                .processingMode(ProcessingMode.SEQUENTIAL)
                .status(JobStatus.PENDING)
                .build();

        when(processingJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(processingJobRepository.save(any(ProcessingJob.class))).thenAnswer(i -> i.getArgument(0));

        // Mock DB duplicate check for MED003 with batch B103
        when(inventoryItemRepository.existsByCodeAndBatchNumber(eq("MED003"), eq("B103"))).thenReturn(true);
        when(inventoryItemRepository.existsByCodeAndBatchNumber(eq("MED001"), eq("B101"))).thenReturn(false);
        when(inventoryItemRepository.existsByCodeAndBatchNumber(eq("MED002"), eq("B102"))).thenReturn(false);

        // CSV content:
        // Row 1: Valid
        // Row 2: In-file duplicate of Row 1 (same MED001 + B101)
        // Row 3: Valid
        // Row 4: In-DB duplicate (MED003 + B103 exists in DB)
        // Row 5: Invalid (negative quantity -10)
        // Row 6: Invalid (invalid price 0.00)
        // Row 7: Invalid (bad date format)
        String csv = """
                code,name,batch_number,quantity,price,expiry_date
                MED001,Paracetamol 500mg,B101,100,12.50,2027-12-31
                MED001,Paracetamol 500mg,B101,50,12.50,2027-12-31
                MED002,Amoxicillin 250mg,B102,200,45.00,2026-06-30
                MED003,Ibuprofen 400mg,B103,150,25.00,2028-01-15
                MED004,Aspirin 100mg,B104,-10,5.00,2027-05-20
                MED005,Cough Syrup,B105,80,0.00,2026-11-10
                MED006,Vitamin C,B106,60,15.00,not-a-date
                """;

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ProcessingReportDto report = sequentialProcessingService.process(jobId, is);

        // Verify summary counts
        assertThat(report.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(report.getTotalRecords()).isEqualTo(7L);
        assertThat(report.getSuccessfulRecords()).isEqualTo(2L); // MED001 (row 1) and MED002 (row 3)
        assertThat(report.getDuplicateRecords()).isEqualTo(2L);  // Row 2 (in-file) + Row 4 (in-DB)
        assertThat(report.getFailedRecords()).isEqualTo(3L);     // Rows 5, 6, 7

        // Verify metrics
        assertThat(report.getExecutionTimeMs()).isGreaterThanOrEqualTo(1L);
        assertThat(report.getThroughputRecordsPerSec()).isNotNull();
        assertThat(report.getPeakMemoryMb()).isNotNull();

        // Verify batch writer interactions
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InventoryItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchWriter, atLeastOnce()).saveInventoryBatch(itemsCaptor.capture());
        List<InventoryItem> savedItems = itemsCaptor.getValue();
        assertThat(savedItems).hasSize(2);
        assertThat(savedItems.get(0).getCode()).isEqualTo("MED001");
        assertThat(savedItems.get(1).getCode()).isEqualTo("MED002");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProcessingError>> errorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchWriter, atLeastOnce()).saveErrorBatch(errorsCaptor.capture());
        List<ProcessingError> savedErrors = errorsCaptor.getValue();
        // 2 duplicates + 3 validation errors = 5 errors total
        assertThat(savedErrors).hasSize(5);

        // Verify error details and exact row numbers
        ProcessingError row2Error = savedErrors.stream().filter(e -> e.getRowNumber() == 2L).findFirst().orElseThrow();
        assertThat(row2Error.getErrorType()).isEqualTo("DUPLICATE");
        assertThat(row2Error.getErrorMessage()).contains("appears more than once in the uploaded file");

        ProcessingError row4Error = savedErrors.stream().filter(e -> e.getRowNumber() == 4L).findFirst().orElseThrow();
        assertThat(row4Error.getErrorType()).isEqualTo("DUPLICATE");
        assertThat(row4Error.getErrorMessage()).contains("already exists in the database");

        ProcessingError row5Error = savedErrors.stream().filter(e -> e.getRowNumber() == 5L).findFirst().orElseThrow();
        assertThat(row5Error.getErrorType()).isEqualTo("VALIDATION_ERROR");
        assertThat(row5Error.getErrorMessage()).contains("quantity must be >= 0");

        ProcessingError row6Error = savedErrors.stream().filter(e -> e.getRowNumber() == 6L).findFirst().orElseThrow();
        assertThat(row6Error.getErrorType()).isEqualTo("VALIDATION_ERROR");
        assertThat(row6Error.getErrorMessage()).contains("price must be > 0");

        ProcessingError row7Error = savedErrors.stream().filter(e -> e.getRowNumber() == 7L).findFirst().orElseThrow();
        assertThat(row7Error.getErrorType()).isEqualTo("VALIDATION_ERROR");
        assertThat(row7Error.getErrorMessage()).contains("expiry_date");
    }
}
