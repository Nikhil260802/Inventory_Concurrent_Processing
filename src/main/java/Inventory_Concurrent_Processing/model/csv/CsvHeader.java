package Inventory_Concurrent_Processing.model.csv;

/**
 * Defines the expected column positions and canonical header names for
 * inventory CSV files supplied by providers.
 *
 * <p>The parser validates that the CSV header row contains exactly these
 * column names (case-insensitive, order-independent). Column indices are
 * resolved at parse time so the order in the file is flexible.
 *
 * <p><b>Expected CSV header (example — order may vary):</b>
 * <pre>
 *   code,name,batch_number,quantity,price,expiry_date
 * </pre>
 */
public final class CsvHeader {

    private CsvHeader() { /* utility class */ }

    // ── Canonical column names (lower-cased for comparison) ──────────────────

    public static final String CODE         = "code";
    public static final String NAME         = "name";
    public static final String BATCH_NUMBER = "batch_number";
    public static final String QUANTITY     = "quantity";
    public static final String PRICE        = "price";
    public static final String EXPIRY_DATE  = "expiry_date";

    /** Total number of required columns. */
    public static final int REQUIRED_COLUMN_COUNT = 6;

    /**
     * All required column names in a fixed reference order.
     * Used to validate the header row and build error messages.
     */
    public static final String[] REQUIRED_COLUMNS = {
            CODE, NAME, BATCH_NUMBER, QUANTITY, PRICE, EXPIRY_DATE
    };
}
