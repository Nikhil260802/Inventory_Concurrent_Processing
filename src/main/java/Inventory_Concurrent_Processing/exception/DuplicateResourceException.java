package Inventory_Concurrent_Processing.exception;

/**
 * Thrown when an inventory record with identical business key already exists.
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
