package Inventory_Concurrent_Processing.model.validation;

/**
 * Outcome of validating a single CSV record.
 *
 * <ul>
 *   <li>{@code VALID}     – all rules passed; record can be persisted.</li>
 *   <li>{@code INVALID}   – one or more field-level rules failed.</li>
 *   <li>{@code DUPLICATE} – business key already exists (in-file or in-DB).</li>
 * </ul>
 */
public enum ValidationStatus {
    VALID,
    INVALID,
    DUPLICATE
}
