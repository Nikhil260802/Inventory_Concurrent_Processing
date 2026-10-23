package Inventory_Concurrent_Processing.validation;

import Inventory_Concurrent_Processing.model.csv.CsvRecord;
import Inventory_Concurrent_Processing.model.validation.ParsedInventoryRecord;
import Inventory_Concurrent_Processing.model.validation.ValidationResult;
import Inventory_Concurrent_Processing.repository.InventoryItemRepository;
import Inventory_Concurrent_Processing.service.validation.DuplicateDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateDetectorTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    private DuplicateDetector duplicateDetector;

    @BeforeEach
    void setUp() {
        duplicateDetector = new DuplicateDetector(inventoryItemRepository);
    }

    @Test
    @DisplayName("Should detect in-file duplicates when same code and batch number appear again in the same CSV")
    void testInFileDuplicateDetection() {
        CsvRecord r1 = CsvRecord.builder().rowNumber(1L).code("MED-001").batchNumber("B101").build();
        ParsedInventoryRecord p1 = ParsedInventoryRecord.builder()
                .rowNumber(1L).code("MED-001").name("Item 1").batchNumber("B101")
                .quantity(10).price(BigDecimal.TEN).expiryDate(LocalDate.now().plusDays(100)).build();

        CsvRecord r2 = CsvRecord.builder().rowNumber(2L).code("MED-001").batchNumber("B101").build();
        ParsedInventoryRecord p2 = ParsedInventoryRecord.builder()
                .rowNumber(2L).code("MED-001").name("Item 1").batchNumber("B101")
                .quantity(20).price(BigDecimal.TEN).expiryDate(LocalDate.now().plusDays(100)).build();

        when(inventoryItemRepository.existsByCodeAndBatchNumber("MED-001", "B101")).thenReturn(false);

        // First occurrence -> unique
        ValidationResult check1 = duplicateDetector.checkDuplicate(r1, p1);
        assertThat(check1.isValid()).isTrue();

        // Second occurrence in same file -> in-file duplicate
        ValidationResult check2 = duplicateDetector.checkDuplicate(r2, p2);
        assertThat(check2.isDuplicate()).isTrue();
        assertThat(check2.getDuplicateReason()).contains("appears more than once in the uploaded file");
    }

    @Test
    @DisplayName("Should detect database duplicate when business key already exists in DB")
    void testInDatabaseDuplicateDetection() {
        CsvRecord r = CsvRecord.builder().rowNumber(1L).code("MED-DB").batchNumber("B-EXIST").build();
        ParsedInventoryRecord p = ParsedInventoryRecord.builder()
                .rowNumber(1L).code("MED-DB").name("Existing Medicine").batchNumber("B-EXIST")
                .quantity(10).price(BigDecimal.TEN).expiryDate(LocalDate.now().plusDays(100)).build();

        when(inventoryItemRepository.existsByCodeAndBatchNumber("MED-DB", "B-EXIST")).thenReturn(true);

        ValidationResult check = duplicateDetector.checkDuplicate(r, p);

        assertThat(check.isDuplicate()).isTrue();
        assertThat(check.getDuplicateReason()).contains("already exists in the database");
    }
}
