package Inventory_Concurrent_Processing.model.validation;

import Inventory_Concurrent_Processing.model.csv.CsvRecord;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of running all validation rules against a single {@link CsvRecord}.
 *
 * <h3>Usage pattern</h3>
 * <pre>{@code
 * ValidationResult result = validator.validate(record);
 *
 * switch (result.getStatus()) {
 *     case VALID     -> persist(result.getParsedRecord());
 *     case INVALID   -> logErrors(result.getErrors());
 *     case DUPLICATE -> incrementDuplicateCounter();
 * }
 * }</pre>
 *
 * <p>Instances are created only through the static factory methods
 * {@link #valid}, {@link #invalid}, and {@link #duplicate}.
 */
@Getter
public final class ValidationResult {

    private final ValidationStatus status;

    /** The original raw CSV record that was validated. */
    private final CsvRecord sourceRecord;

    /**
     * Non-null only when {@code status == VALID}.
     * Contains type-converted, business-rule-checked field values
     * ready to be mapped to an {@link Inventory_Concurrent_Processing.entity.InventoryItem}.
     */
    private final ParsedInventoryRecord parsedRecord;

    /** Non-empty only when {@code status == INVALID}. */
    private final List<FieldError> errors;

    /** Non-null only when {@code status == DUPLICATE}. */
    private final String duplicateReason;

    // ── Private constructor ───────────────────────────────────────────────────

    private ValidationResult(
            ValidationStatus status,
            CsvRecord sourceRecord,
            ParsedInventoryRecord parsedRecord,
            List<FieldError> errors,
            String duplicateReason) {
        this.status          = status;
        this.sourceRecord    = sourceRecord;
        this.parsedRecord    = parsedRecord;
        this.errors          = errors != null ? Collections.unmodifiableList(errors) : List.of();
        this.duplicateReason = duplicateReason;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /**
     * Creates a VALID result that carries the parsed domain record.
     *
     * @param source raw CSV record that was validated
     * @param parsed type-converted, business-rule-checked record
     * @return VALID result
     */
    public static ValidationResult valid(CsvRecord source, ParsedInventoryRecord parsed) {
        return new ValidationResult(ValidationStatus.VALID, source, parsed, null, null);
    }

    /**
     * Creates an INVALID result that carries one or more field errors.
     *
     * @param source raw CSV record that failed validation
     * @param errors list of per-field violations (must not be empty)
     * @return INVALID result
     */
    public static ValidationResult invalid(CsvRecord source, List<FieldError> errors) {
        return new ValidationResult(ValidationStatus.INVALID, source, null, new ArrayList<>(errors), null);
    }

    /**
     * Creates a DUPLICATE result.
     *
     * @param source raw CSV record identified as a duplicate
     * @param reason human-readable explanation (e.g. "code=MED001, batch=B-2024 already in DB")
     * @return DUPLICATE result
     */
    public static ValidationResult duplicate(CsvRecord source, String reason) {
        return new ValidationResult(ValidationStatus.DUPLICATE, source, null, null, reason);
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    public boolean isValid()     { return status == ValidationStatus.VALID;     }
    public boolean isInvalid()   { return status == ValidationStatus.INVALID;   }
    public boolean isDuplicate() { return status == ValidationStatus.DUPLICATE; }

    /**
     * Builds a compact, single-string error summary from all field errors.
     * Useful for storing in {@link Inventory_Concurrent_Processing.entity.ProcessingError#getErrorMessage()}.
     *
     * @return comma-joined field error messages, or the duplicate reason
     */
    public String buildErrorMessage() {
        if (isDuplicate()) return "DUPLICATE: " + duplicateReason;
        if (errors.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(errors.get(i));
        }
        return sb.toString();
    }
}
