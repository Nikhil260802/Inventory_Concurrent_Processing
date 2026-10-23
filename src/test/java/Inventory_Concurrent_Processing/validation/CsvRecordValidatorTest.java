package Inventory_Concurrent_Processing.validation;

import Inventory_Concurrent_Processing.model.csv.CsvRecord;
import Inventory_Concurrent_Processing.model.validation.ParsedInventoryRecord;
import Inventory_Concurrent_Processing.model.validation.ValidationResult;
import Inventory_Concurrent_Processing.service.validation.CsvRecordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CsvRecordValidatorTest {

    private CsvRecordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CsvRecordValidator();
    }

    @Test
    @DisplayName("Should pass validation when all fields are valid")
    void testValidRecord() {
        CsvRecord record = CsvRecord.builder()
                .rowNumber(1L)
                .code("MED-001")
                .name("Paracetamol 500mg")
                .batchNumber("B101")
                .quantity("150")
                .price("12.50")
                .expiryDate("2027-12-31")
                .rawLine("MED-001,Paracetamol 500mg,B101,150,12.50,2027-12-31")
                .build();

        ValidationResult result = validator.validate(record);

        assertThat(result.isValid()).isTrue();
        ParsedInventoryRecord parsed = result.getParsedRecord();
        assertThat(parsed.getCode()).isEqualTo("MED-001");
        assertThat(parsed.getName()).isEqualTo("Paracetamol 500mg");
        assertThat(parsed.getBatchNumber()).isEqualTo("B101");
        assertThat(parsed.getQuantity()).isEqualTo(150);
        assertThat(parsed.getPrice()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(parsed.getExpiryDate()).isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027-12-31", "31-12-2027", "31/12/2027", "12/31/2027", "2027/12/31"})
    @DisplayName("Should accept all standard date format variations")
    void testAcceptedDateFormats(String dateStr) {
        CsvRecord record = CsvRecord.builder()
                .rowNumber(2L)
                .code("MED-002")
                .name("Aspirin")
                .batchNumber("B102")
                .quantity("50")
                .price("5.00")
                .expiryDate(dateStr)
                .build();

        ValidationResult result = validator.validate(record);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getParsedRecord().getExpiryDate()).isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @Test
    @DisplayName("Should fail when quantity is negative or non-integer")
    void testInvalidQuantity() {
        CsvRecord negativeQty = CsvRecord.builder()
                .rowNumber(3L)
                .code("MED-003")
                .name("Ibuprofen")
                .batchNumber("B103")
                .quantity("-10")
                .price("15.00")
                .expiryDate("2028-01-01")
                .build();

        ValidationResult res1 = validator.validate(negativeQty);
        assertThat(res1.isInvalid()).isTrue();
        assertThat(res1.buildErrorMessage()).contains("quantity must be >= 0");

        CsvRecord nonIntQty = CsvRecord.builder()
                .rowNumber(3L)
                .code("MED-003")
                .name("Ibuprofen")
                .batchNumber("B103")
                .quantity("10.5")
                .price("15.00")
                .expiryDate("2028-01-01")
                .build();

        ValidationResult res2 = validator.validate(nonIntQty);
        assertThat(res2.isInvalid()).isTrue();
        assertThat(res2.buildErrorMessage()).contains("quantity must be a whole number");
    }

    @Test
    @DisplayName("Should fail when price is zero, negative, or exceeds 2 decimal places")
    void testInvalidPrice() {
        CsvRecord zeroPrice = CsvRecord.builder()
                .rowNumber(4L)
                .code("MED-004")
                .name("Cetirizine")
                .batchNumber("B104")
                .quantity("10")
                .price("0.00")
                .expiryDate("2027-10-10")
                .build();

        ValidationResult res1 = validator.validate(zeroPrice);
        assertThat(res1.isInvalid()).isTrue();
        assertThat(res1.buildErrorMessage()).contains("price must be > 0");

        CsvRecord threeDecimals = CsvRecord.builder()
                .rowNumber(4L)
                .code("MED-004")
                .name("Cetirizine")
                .batchNumber("B104")
                .quantity("10")
                .price("12.345")
                .expiryDate("2027-10-10")
                .build();

        ValidationResult res2 = validator.validate(threeDecimals);
        assertThat(res2.isInvalid()).isTrue();
        assertThat(res2.buildErrorMessage()).contains("at most 2 decimal places");
    }

    @Test
    @DisplayName("Should report multiple field errors in a single row without stopping at the first failure")
    void testMultipleErrorsInOneRow() {
        CsvRecord multiError = CsvRecord.builder()
                .rowNumber(5L)
                .code("")            // missing code
                .name("   ")         // blank name
                .batchNumber("B105")
                .quantity("-20")     // negative quantity
                .price("-5.00")      // negative price
                .expiryDate("invalid-date") // bad date
                .build();

        ValidationResult result = validator.validate(multiError);

        assertThat(result.isInvalid()).isTrue();
        assertThat(result.getErrors()).hasSize(5);
        String msg = result.buildErrorMessage();
        assertThat(msg).contains("code is mandatory");
        assertThat(msg).contains("name is mandatory");
        assertThat(msg).contains("quantity must be >= 0");
        assertThat(msg).contains("price must be > 0");
        assertThat(msg).contains("does not match any accepted format");
    }
}
