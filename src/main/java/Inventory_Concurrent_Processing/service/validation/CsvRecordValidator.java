package Inventory_Concurrent_Processing.service.validation;

import Inventory_Concurrent_Processing.model.csv.CsvRecord;
import Inventory_Concurrent_Processing.model.validation.FieldError;
import Inventory_Concurrent_Processing.model.validation.ParsedInventoryRecord;
import Inventory_Concurrent_Processing.model.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * <h2>CSV Record Validator</h2>
 *
 * Validates a single {@link CsvRecord} against all field-level and
 * business rules, returning a {@link ValidationResult} that captures
 * every violation found in that row (fail-all, not fail-fast).
 *
 * <h3>Rules enforced</h3>
 * <table border="1">
 *   <tr><th>Field</th><th>Rules</th></tr>
 *   <tr><td>code</td><td>mandatory, non-blank, max 100 chars</td></tr>
 *   <tr><td>name</td><td>mandatory, non-blank, max 255 chars</td></tr>
 *   <tr><td>batch_number</td><td>mandatory, non-blank, max 100 chars</td></tr>
 *   <tr><td>quantity</td><td>mandatory, parseable as integer, &ge; 0</td></tr>
 *   <tr><td>price</td><td>mandatory, parseable as decimal, &gt; 0, max 2 decimal places</td></tr>
 *   <tr><td>expiry_date</td><td>mandatory, valid date in one of the accepted formats</td></tr>
 * </table>
 *
 * <h3>Thread safety</h3>
 * <p>This class is <em>stateless</em> and safe to call from multiple threads
 * simultaneously — important for the parallel processing pipeline.
 *
 * <h3>Duplicate detection</h3>
 * <p>This validator checks only field-level rules. In-file and in-database
 * duplicate detection is performed by
 * {@link Inventory_Concurrent_Processing.service.validation.DuplicateDetector},
 * which is called separately after this validator confirms the record is VALID.
 */
@Component
public class CsvRecordValidator {

    private static final Logger log = LoggerFactory.getLogger(CsvRecordValidator.class);

    // ── Accepted date formats (provider files may use any of these) ───────────
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    // ── Field length limits (mirror DB column lengths) ────────────────────────
    private static final int MAX_CODE_LEN         = 100;
    private static final int MAX_NAME_LEN         = 255;
    private static final int MAX_BATCH_NUMBER_LEN = 100;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates all fields of the given {@link CsvRecord}.
     *
     * <p>All rules are evaluated regardless of prior failures so that a single
     * pass produces a complete error list per row.
     *
     * @param record the raw parsed CSV row to validate
     * @return {@link ValidationResult#valid} if all rules pass,
     *         {@link ValidationResult#invalid} with the full error list otherwise
     */
    public ValidationResult validate(CsvRecord record) {
        List<FieldError> errors = new ArrayList<>();

        // ── Mandatory text fields ─────────────────────────────────────────────
        String code        = validateText(record.getCode(),        "code",         MAX_CODE_LEN,         errors);
        String name        = validateText(record.getName(),        "name",         MAX_NAME_LEN,         errors);
        String batchNumber = validateText(record.getBatchNumber(), "batch_number", MAX_BATCH_NUMBER_LEN, errors);

        // ── Quantity ──────────────────────────────────────────────────────────
        Integer quantity = validateQuantity(CsvRecord.safe(record.getQuantity()), errors);

        // ── Price ─────────────────────────────────────────────────────────────
        BigDecimal price = validatePrice(CsvRecord.safe(record.getPrice()), errors);

        // ── Expiry date ───────────────────────────────────────────────────────
        LocalDate expiryDate = validateExpiryDate(CsvRecord.safe(record.getExpiryDate()), errors);

        // ── Result ────────────────────────────────────────────────────────────
        if (!errors.isEmpty()) {
            log.debug("Row {} failed validation with {} error(s)", record.getRowNumber(), errors.size());
            return ValidationResult.invalid(record, errors);
        }

        ParsedInventoryRecord parsed = ParsedInventoryRecord.builder()
                .code(code)
                .name(name)
                .batchNumber(batchNumber)
                .quantity(quantity)
                .price(price)
                .expiryDate(expiryDate)
                .rowNumber(record.getRowNumber())
                .build();

        return ValidationResult.valid(record, parsed);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private rule methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Validates a mandatory text field: non-null, non-blank, within max length.
     *
     * @param raw       raw string from the CSV (may be null or blank)
     * @param fieldName column name used in error messages
     * @param maxLen    maximum allowed character length
     * @param errors    accumulator — a {@link FieldError} is added on violation
     * @return trimmed value if valid, {@code null} otherwise
     */
    private String validateText(String raw, String fieldName, int maxLen, List<FieldError> errors) {
        String value = CsvRecord.safe(raw);
        if (value.isEmpty()) {
            errors.add(new FieldError(fieldName, fieldName + " is mandatory and must not be blank", raw == null ? "" : raw));
            return null;
        }
        if (value.length() > maxLen) {
            errors.add(new FieldError(fieldName,
                    fieldName + " exceeds maximum length of " + maxLen + " characters (got " + value.length() + ")",
                    value));
            return null;
        }
        return value;
    }

    /**
     * Validates the {@code quantity} field:
     * <ul>
     *   <li>Non-blank</li>
     *   <li>Parseable as a whole integer</li>
     *   <li>Value &ge; 0</li>
     * </ul>
     *
     * @param raw    trimmed raw string from the CSV
     * @param errors accumulator
     * @return parsed integer, or {@code null} on any violation
     */
    private Integer validateQuantity(String raw, List<FieldError> errors) {
        if (raw.isEmpty()) {
            errors.add(new FieldError("quantity", "quantity is mandatory and must not be blank", raw));
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            errors.add(new FieldError("quantity",
                    "quantity must be a whole number (integer) but was '" + raw + "'", raw));
            return null;
        }
        if (value < 0) {
            errors.add(new FieldError("quantity",
                    "quantity must be >= 0 but was " + value, raw));
            return null;
        }
        return value;
    }

    /**
     * Validates the {@code price} field:
     * <ul>
     *   <li>Non-blank</li>
     *   <li>Parseable as a decimal number</li>
     *   <li>Value &gt; 0</li>
     *   <li>At most 2 decimal places (to fit the DB column precision)</li>
     * </ul>
     *
     * @param raw    trimmed raw string from the CSV
     * @param errors accumulator
     * @return parsed {@link BigDecimal}, or {@code null} on any violation
     */
    private BigDecimal validatePrice(String raw, List<FieldError> errors) {
        if (raw.isEmpty()) {
            errors.add(new FieldError("price", "price is mandatory and must not be blank", raw));
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            errors.add(new FieldError("price",
                    "price must be a valid decimal number but was '" + raw + "'", raw));
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new FieldError("price",
                    "price must be > 0 but was " + value, raw));
            return null;
        }
        if (value.scale() > 2) {
            errors.add(new FieldError("price",
                    "price must have at most 2 decimal places but was '" + raw + "'", raw));
            return null;
        }
        return value;
    }

    /**
     * Validates the {@code expiry_date} field:
     * <ul>
     *   <li>Non-blank</li>
     *   <li>Parseable by one of the accepted date formatters</li>
     * </ul>
     * Accepted formats: {@code yyyy-MM-dd}, {@code dd-MM-yyyy},
     * {@code dd/MM/yyyy}, {@code MM/dd/yyyy}, {@code yyyy/MM/dd}.
     *
     * @param raw    trimmed raw string from the CSV
     * @param errors accumulator
     * @return parsed {@link LocalDate}, or {@code null} on any violation
     */
    private LocalDate validateExpiryDate(String raw, List<FieldError> errors) {
        if (raw.isEmpty()) {
            errors.add(new FieldError("expiry_date",
                    "expiry_date is mandatory and must not be blank", raw));
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        errors.add(new FieldError("expiry_date",
                "expiry_date '" + raw + "' does not match any accepted format: "
                + "yyyy-MM-dd, dd-MM-yyyy, dd/MM/yyyy, MM/dd/yyyy, yyyy/MM/dd",
                raw));
        return null;
    }
}
