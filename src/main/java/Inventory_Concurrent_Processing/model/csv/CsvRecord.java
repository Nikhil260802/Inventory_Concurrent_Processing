package Inventory_Concurrent_Processing.model.csv;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable snapshot of a single raw CSV row <em>before</em> any type
 * conversion or business-rule validation.
 *
 * <p>All fields are kept as {@code String} intentionally — type-parsing errors
 * are caught and reported in the validation layer with clear messages and the
 * exact row number, rather than blowing up inside the parser.
 *
 * <p>The {@code rowNumber} is 1-based (header = 0, first data row = 1) so it
 * aligns directly with what a human sees when they open the file in Excel.
 */
@Getter
@Builder
public final class CsvRecord {

    /** 1-based position of this row in the uploaded file (header row = 0). */
    private final long rowNumber;

    // ── Raw field values exactly as they appear in the CSV ───────────────────

    /** Column: code — provider-assigned entry / medicine code. */
    private final String code;

    /** Column: name — human-readable item name. */
    private final String name;

    /** Column: batch_number — manufacturing batch / lot number. */
    private final String batchNumber;

    /** Column: quantity — available stock count (must parse to int ≥ 0). */
    private final String quantity;

    /** Column: price — unit price (must parse to BigDecimal > 0). */
    private final String price;

    /** Column: expiry_date — batch expiry (must parse to LocalDate). */
    private final String expiryDate;

    /**
     * The verbatim raw line from the CSV file, capped at 2 000 characters.
     * Stored on {@link Inventory_Concurrent_Processing.entity.ProcessingError}
     * for debugging without reopening the source file.
     */
    private final String rawLine;

    // ── Convenience ──────────────────────────────────────────────────────────

    /**
     * Returns a trimmed, {@code null}-safe view of a field value.
     * Useful in the validation layer when checking for blank/empty strings.
     *
     * @param value any string (may be {@code null})
     * @return trimmed string, or empty string when {@code null}
     */
    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public String toString() {
        return "CsvRecord{row=" + rowNumber + ", code='" + code + "', batch='" + batchNumber + "'}";
    }
}
