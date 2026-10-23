package Inventory_Concurrent_Processing.enums;

/**
 * Execution strategy used to process a CSV file.
 */
public enum ProcessingMode {

    /** Single-threaded baseline pipeline. */
    SEQUENTIAL,

    /** Multi-threaded pipeline backed by a bounded thread pool. */
    PARALLEL
}
