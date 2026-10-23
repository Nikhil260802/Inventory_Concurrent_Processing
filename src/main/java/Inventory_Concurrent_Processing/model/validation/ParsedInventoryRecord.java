package Inventory_Concurrent_Processing.model.validation;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A fully type-converted, business-rule-validated snapshot of one CSV row.
 *
 * <p>Created by {@link Inventory_Concurrent_Processing.service.validation.CsvRecordValidator}
 * only when ALL field rules pass. It is then mapped to an
 * {@link Inventory_Concurrent_Processing.entity.InventoryItem} for persistence.
 *
 * <p>All fields carry their proper Java types (no raw strings), so the
 * persistence layer never needs to parse or re-validate values.
 */
@Getter
@Builder
public final class ParsedInventoryRecord {

    /** Provider-assigned entry / medicine code. Trimmed, non-blank. */
    private final String code;

    /** Human-readable item name. Trimmed, non-blank. */
    private final String name;

    /** Manufacturing batch / lot number. Trimmed, non-blank. */
    private final String batchNumber;

    /** Available stock count. Guaranteed &ge; 0. */
    private final int quantity;

    /** Unit price. Guaranteed &gt; 0. */
    private final BigDecimal price;

    /** Batch expiry date. Guaranteed non-null and a valid calendar date. */
    private final LocalDate expiryDate;

    /** 1-based row number in the original CSV (for traceability). */
    private final long rowNumber;
}
