package Inventory_Concurrent_Processing.service.validation;

import Inventory_Concurrent_Processing.model.csv.CsvRecord;
import Inventory_Concurrent_Processing.model.validation.ParsedInventoryRecord;
import Inventory_Concurrent_Processing.model.validation.ValidationResult;
import Inventory_Concurrent_Processing.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h2>Duplicate Detector</h2>
 *
 * Detects duplicate inventory records at two levels:
 *
 * <ol>
 *   <li><b>In-file duplicates</b> — two or more rows in the <em>same CSV upload</em>
 *       share the same business key ({@code code + batchNumber}).  Detected using a
 *       {@link ConcurrentHashMap}-backed seen-key set that is scoped to one job.</li>
 *   <li><b>In-database duplicates</b> — a record with the same business key already
 *       exists in the {@code inventory} table from a <em>previous</em> upload.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>The {@code seenKeys} set uses {@link ConcurrentHashMap#newKeySet()}, which is
 * safe for concurrent access from multiple worker threads in the parallel pipeline.
 * The {@link #reset()} method <em>must</em> be called once per job (before any
 * records are checked) and only from a single thread (the job-orchestration thread).
 *
 * <h3>Important</h3>
 * <p>This component is <strong>prototype-scoped</strong> — each processing job
 * receives its own instance (and therefore its own {@code seenKeys} set).
 * Do NOT use it as a singleton shared across jobs.
 */
@Component
@org.springframework.context.annotation.Scope("prototype")
public class DuplicateDetector {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetector.class);

    private final InventoryItemRepository inventoryItemRepository;

    /** Thread-safe set of business keys seen so far in the current CSV file. */
    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();

    public DuplicateDetector(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks whether a validated record is a duplicate.
     *
     * <p>Call this <em>only</em> for records that already passed field
     * validation (i.e. {@link ValidationResult#isValid()} == true).
     *
     * @param source the original raw CSV record (for the result's sourceRecord)
     * @param parsed the type-converted record whose business key to check
     * @return a {@link ValidationResult#duplicate} if a duplicate is found,
     *         or the original {@code validResult} unchanged
     */
    public ValidationResult checkDuplicate(CsvRecord source, ParsedInventoryRecord parsed) {
        String key = buildKey(parsed.getCode(), parsed.getBatchNumber());

        // 1. In-file duplicate check (O(1), thread-safe)
        if (!seenKeys.add(key)) {
            String reason = "code='" + parsed.getCode()
                    + "', batch_number='" + parsed.getBatchNumber()
                    + "' appears more than once in the uploaded file";
            log.debug("Row {} – in-file duplicate: {}", parsed.getRowNumber(), key);
            return ValidationResult.duplicate(source, reason);
        }

        // 2. In-database duplicate check
        if (isKeyInDatabase(key, parsed.getCode(), parsed.getBatchNumber())) {
            String reason = "code='" + parsed.getCode()
                    + "', batch_number='" + parsed.getBatchNumber()
                    + "' already exists in the database";
            log.debug("Row {} – DB duplicate: {}", parsed.getRowNumber(), key);
            return ValidationResult.duplicate(source, reason);
        }

        return ValidationResult.valid(source, parsed);
    }

    /**
     * Checks if a key exists in DB, leveraging pre-cached DB keys if available,
     * or querying the repository directly.
     */
    private boolean isKeyInDatabase(String key, String code, String batchNumber) {
        if (dbKeysCache.contains(key)) {
            return true;
        }
        boolean exists = inventoryItemRepository.existsByCodeAndBatchNumber(code, batchNumber);
        if (exists) {
            dbKeysCache.add(key);
        }
        return exists;
    }

    /** Thread-safe cache of known DB keys to eliminate repeated DB round-trips. */
    private final Set<String> dbKeysCache = ConcurrentHashMap.newKeySet();

    /**
     * Optional pre-population of known DB keys for a batch of codes.
     */
    public void registerDbDuplicates(java.util.Collection<String> keys) {
        dbKeysCache.addAll(keys);
    }

    /**
     * Clears both the in-file seen-key set and DB key cache.
     * Must be called once per job before processing begins.
     */
    public void reset() {
        seenKeys.clear();
        dbKeysCache.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a normalised, lowercase composite business key.
     *
     * @param code        item code
     * @param batchNumber batch number
     * @return composite key string
     */
    public static String buildKey(String code, String batchNumber) {
        return code.trim().toLowerCase() + "::" + batchNumber.trim().toLowerCase();
    }
}
