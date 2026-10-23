package Inventory_Concurrent_Processing.repository;

import Inventory_Concurrent_Processing.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link InventoryItem}.
 *
 * <p>All search methods are case-insensitive and support partial matching
 * where noted. Pagination and sorting are delegated to the caller via
 * {@link Pageable}, which maps directly to the REST layer's request params.
 */
@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    // -------------------------------------------------------------------------
    // Business-key lookup (used for duplicate detection)
    // -------------------------------------------------------------------------

    /**
     * Finds an item by its composite business key (code + batch number).
     * Used before insertion to detect cross-session duplicates.
     *
     * @param code        the provider-assigned entry code
     * @param batchNumber the manufacturing batch / lot number
     * @return the matching item, or empty if not present
     */
    Optional<InventoryItem> findByCodeAndBatchNumber(String code, String batchNumber);

    /**
     * Returns {@code true} if a record with the given business key already
     * exists in the database. Slightly cheaper than loading the full entity.
     *
     * @param code        the provider-assigned entry code
     * @param batchNumber the manufacturing batch / lot number
     * @return {@code true} when a duplicate is found
     */
    boolean existsByCodeAndBatchNumber(String code, String batchNumber);

    // -------------------------------------------------------------------------
    // Search API queries (paginated + sortable)
    // -------------------------------------------------------------------------

    /**
     * Case-insensitive partial search by item name (LIKE %name%).
     *
     * @param name     substring to match against the name column
     * @param pageable pagination and sorting instructions
     * @return page of matching items
     */
    Page<InventoryItem> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Case-insensitive partial search by entry code (LIKE %code%).
     *
     * @param code     substring to match against the code column
     * @param pageable pagination and sorting instructions
     * @return page of matching items
     */
    Page<InventoryItem> findByCodeContainingIgnoreCase(String code, Pageable pageable);

    /**
     * Case-insensitive partial search by batch number (LIKE %batchNumber%).
     *
     * @param batchNumber substring to match against the batch_number column
     * @param pageable    pagination and sorting instructions
     * @return page of matching items
     */
    Page<InventoryItem> findByBatchNumberContainingIgnoreCase(String batchNumber, Pageable pageable);

    /**
     * Combined search across name, code, and batch number — any field matches.
     * Useful for a single "global search" query parameter in the REST API.
     *
     * @param name        substring for name
     * @param code        substring for code
     * @param batchNumber substring for batch number
     * @param pageable    pagination and sorting instructions
     * @return page of items matching any of the three criteria
     */
    @Query("""
            SELECT i FROM InventoryItem i
            WHERE LOWER(i.name)        LIKE LOWER(CONCAT('%', :name,        '%'))
               OR LOWER(i.code)        LIKE LOWER(CONCAT('%', :code,        '%'))
               OR LOWER(i.batchNumber) LIKE LOWER(CONCAT('%', :batchNumber, '%'))
            """)
    Page<InventoryItem> searchByAnyField(
            @Param("name")        String name,
            @Param("code")        String code,
            @Param("batchNumber") String batchNumber,
            Pageable pageable
    );
}
