package Inventory_Concurrent_Processing.exception;

/**
 * Thrown when a structural problem is detected in a CSV file that prevents
 * further parsing — for example, a missing or incorrect header row,
 * an unterminated quoted field, or an I/O failure mid-stream.
 *
 * <p>Per-row field-level errors (wrong type, bad date, negative price, etc.)
 * are <em>not</em> reported via this exception; they are collected into
 * {@code ProcessingError} records so that processing can continue for the
 * remaining rows.
 */
public class CsvParseException extends RuntimeException {

    private final long lineNumber;

    public CsvParseException(String message, long lineNumber) {
        super("CSV parse error at line " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    public CsvParseException(String message, long lineNumber, Throwable cause) {
        super("CSV parse error at line " + lineNumber + ": " + message, cause);
        this.lineNumber = lineNumber;
    }

    /** 1-based line number in the raw CSV file where the problem occurred. */
    public long getLineNumber() {
        return lineNumber;
    }
}
