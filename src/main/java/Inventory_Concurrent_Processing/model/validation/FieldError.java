package Inventory_Concurrent_Processing.model.validation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A single field-level validation failure.
 *
 * <p>Each {@link ValidationResult} may contain zero or more {@code FieldError}
 * instances — one per violated rule — so callers receive all problems at once
 * rather than fail-fast on the first error.
 */
@Getter
@RequiredArgsConstructor
public final class FieldError {

    /** Name of the CSV column that triggered the error (e.g. {@code "quantity"}). */
    private final String field;

    /**
     * Human-readable description of what went wrong.
     * Example: {@code "quantity must be >= 0 but was -5"}.
     */
    private final String message;

    /**
     * The raw string value that caused the error (as it appeared in the CSV).
     * May be empty/blank when the field was absent.
     */
    private final String rejectedValue;

    @Override
    public String toString() {
        return "[" + field + "] " + message + " (got: '" + rejectedValue + "')";
    }
}
