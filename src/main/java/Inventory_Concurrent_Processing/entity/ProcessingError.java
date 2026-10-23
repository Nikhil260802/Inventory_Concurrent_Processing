package Inventory_Concurrent_Processing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Records a per-row validation failure or processing error encountered
 * during a CSV processing job.
 *
 * <p>One {@link ProcessingError} row is written for each invalid or
 * duplicate CSV row so that clients can retrieve a detailed, row-numbered
 * error report via the job errors API.
 */
@Entity
@Table(
        name = "processing_error",
        indexes = {
                @Index(name = "idx_error_job_id",    columnList = "job_id"),
                @Index(name = "idx_error_row_number", columnList = "csv_row_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingError {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The job this error belongs to.
     * Using a plain FK column (not a full {@code @ManyToOne}) keeps the
     * aggregate simple and avoids N+1 loads.
     */
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    /**
     * 1-based row number in the uploaded CSV file (header = row 0, first
     * data row = row 1) so the client can locate the offending line quickly.
     * Column named 'csv_row_number' to avoid MySQL 8 reserved keyword collision.
     */
    @Column(name = "csv_row_number", nullable = false)
    private Long rowNumber;

    /**
     * Classification of the problem.
     * E.g. {@code "VALIDATION_ERROR"}, {@code "DUPLICATE"}, {@code "PARSE_ERROR"}.
     */
    @Column(name = "error_type", nullable = false, length = 50)
    private String errorType;

    /**
     * Raw CSV row content (verbatim), trimmed to 2 000 characters to cap
     * storage. Useful for debugging without re-opening the source file.
     */
    @Column(name = "raw_record", columnDefinition = "TEXT")
    private String rawRecord;

    /**
     * Human-readable description of what went wrong.
     * E.g. "quantity must be >= 0 but was -5" or "duplicate: code=MED001, batch=B-2024".
     */
    @Column(name = "error_message", columnDefinition = "TEXT", nullable = false)
    private String errorMessage;

    /** Timestamp when this error record was persisted. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
