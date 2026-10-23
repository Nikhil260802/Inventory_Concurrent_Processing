package Inventory_Concurrent_Processing.entity;

import Inventory_Concurrent_Processing.enums.JobStatus;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tracks the lifecycle and summary statistics of a single CSV processing job.
 *
 * <p>A job is created when a CSV file is uploaded. Its counters are updated
 * atomically (via thread-safe service layer) throughout processing, and its
 * status transitions: PENDING → PROCESSING → COMPLETED | FAILED.
 */
@Entity
@Table(
        name = "processing_job",
        indexes = {
                @Index(name = "idx_job_status", columnList = "status"),
                @Index(name = "idx_job_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingJob {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Original file name as supplied by the client. */
    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    /** Strategy used: SEQUENTIAL or PARALLEL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_mode", nullable = false, length = 20)
    private ProcessingMode processingMode;

    /** Current lifecycle state of the job. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    // -------------------------------------------------------------------------
    // Timing
    // -------------------------------------------------------------------------

    /** Timestamp when the job was accepted / row inserted. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when actual record processing began. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** Timestamp when processing finished (successfully or not). */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    // -------------------------------------------------------------------------
    // Record counters (updated atomically by service layer)
    // -------------------------------------------------------------------------

    /** Total rows read from the CSV (excluding the header row). */
    @Column(name = "total_records", nullable = false)
    @Builder.Default
    private Long totalRecords = 0L;

    /** Rows that passed all validations and were persisted to the DB. */
    @Column(name = "successful_records", nullable = false)
    @Builder.Default
    private Long successfulRecords = 0L;

    /** Rows that failed one or more validation rules. */
    @Column(name = "failed_records", nullable = false)
    @Builder.Default
    private Long failedRecords = 0L;

    /**
     * Rows whose (code, batchNumber) already existed in the DB or
     * appeared earlier in the same CSV file.
     */
    @Column(name = "duplicate_records", nullable = false)
    @Builder.Default
    private Long duplicateRecords = 0L;

    // -------------------------------------------------------------------------
    // Performance metrics (populated after job completion)
    // -------------------------------------------------------------------------

    /** Wall-clock execution time in milliseconds. */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /** Throughput: records evaluated per second (total / seconds elapsed). */
    @Column(name = "throughput_records_per_sec", precision = 10, scale = 2)
    private java.math.BigDecimal throughputRecordsPerSec;

    /** Peak JVM heap used during processing in megabytes (best-effort). */
    @Column(name = "peak_memory_mb", precision = 10, scale = 2)
    private java.math.BigDecimal peakMemoryMb;

    // -------------------------------------------------------------------------
    // Error / abort information
    // -------------------------------------------------------------------------

    /**
     * High-level error message when status = FAILED.
     * Detailed per-row errors are stored in {@link ProcessingError}.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
