package Inventory_Concurrent_Processing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a single inventory item stored after successful CSV validation.
 *
 * <p>Business key: {@code (code, batchNumber)} — enforced at both DB and
 * application levels to prevent duplicate inventory entries.
 */
@Entity
@Table(
        name = "inventory",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_inventory_code_batch",
                        columnNames = {"code", "batch_number"}
                )
        },
        indexes = {
                @Index(name = "idx_inventory_code",         columnList = "code"),
                @Index(name = "idx_inventory_name",         columnList = "name"),
                @Index(name = "idx_inventory_batch_number", columnList = "batch_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    /** Surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique entry / medicine code supplied by the provider.
     * Part of the composite business key.
     */
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    /** Human-readable name of the inventory item (e.g. medicine name). */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Batch / lot number assigned during manufacturing.
     * Part of the composite business key.
     */
    @Column(name = "batch_number", nullable = false, length = 100)
    private String batchNumber;

    /**
     * Available quantity. Must be &ge; 0.
     * Validated at the application layer before persistence.
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Unit price. Must be &gt; 0.
     * Stored with enough precision for monetary values.
     */
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * Expiry / expiration date of the batch.
     * Must be a valid future or present date.
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    /** Timestamp when the record was first inserted (managed by Hibernate). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp of the last update (managed by Hibernate). */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * References the processing job that inserted this record.
     * Kept as a plain foreign key to avoid loading the full Job graph.
     */
    @Column(name = "source_job_id")
    private Long sourceJobId;
}
