package Inventory_Concurrent_Processing.enums;

/**
 * Lifecycle states of a CSV processing job.
 */
public enum JobStatus {

    /** Job has been accepted and is waiting to start. */
    PENDING,

    /** Job is actively processing records. */
    PROCESSING,

    /** Job finished successfully (all records evaluated). */
    COMPLETED,

    /** Job was terminated due to an unrecoverable error. */
    FAILED
}
