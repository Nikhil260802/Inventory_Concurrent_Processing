package Inventory_Concurrent_Processing.service.batch;

import Inventory_Concurrent_Processing.entity.InventoryItem;
import Inventory_Concurrent_Processing.entity.ProcessingError;
import Inventory_Concurrent_Processing.repository.InventoryItemRepository;
import Inventory_Concurrent_Processing.repository.ProcessingErrorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service component dedicated to committing batches of inventory items and processing
 * errors into the database in isolated transactions.
 *
 * <p>Clears the Hibernate first-level cache after every batch commit to prevent
 * memory leaks when streaming large datasets.
 */
@Service
public class InventoryBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(InventoryBatchWriter.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final ProcessingErrorRepository processingErrorRepository;
    private final InventoryBatchWriter self;

    @PersistenceContext
    private EntityManager entityManager;

    public InventoryBatchWriter(
            InventoryItemRepository inventoryItemRepository,
            ProcessingErrorRepository processingErrorRepository,
            @Lazy InventoryBatchWriter self) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.processingErrorRepository = processingErrorRepository;
        this.self = self;
    }

    /**
     * Persists a batch of validated {@link InventoryItem} entities in an isolated transaction.
     *
     * @param items list of inventory items to save
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveInventoryBatch(List<InventoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        log.debug("Persisting batch of {} inventory items", items.size());
        inventoryItemRepository.saveAll(items);
        inventoryItemRepository.flush();
        entityManager.clear();
    }

    /**
     * Persists a batch of inventory items with automatic fallback if a concurrent
     * uniqueness constraint collision occurs.
     *
     * @param items items to persist
     * @return number of successfully saved items
     */
    public int saveInventoryBatchSafely(List<InventoryItem> items, List<ProcessingError> duplicateErrorsOut) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        try {
            self.saveInventoryBatch(items);
            return items.size();
        } catch (DataIntegrityViolationException dive) {
            log.warn("Batch insert encountered uniqueness collision, falling back to per-item persistence (size={})", items.size());
            int saved = 0;
            for (InventoryItem item : items) {
                boolean success = self.saveSingleItem(item);
                if (success) {
                    saved++;
                } else {
                    if (duplicateErrorsOut != null) {
                        duplicateErrorsOut.add(ProcessingError.builder()
                                .jobId(item.getSourceJobId())
                                .rowNumber(0L) // database constraint collision
                                .errorType("DUPLICATE")
                                .rawRecord("code=" + item.getCode() + ", batch=" + item.getBatchNumber())
                                .errorMessage("Concurrent duplicate detected by database unique constraint: code=" 
                                        + item.getCode() + ", batch=" + item.getBatchNumber())
                                .build());
                    }
                }
            }
            return saved;
        }
    }

    /**
     * Saves a single item in its own transaction, returning false if a unique constraint fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveSingleItem(InventoryItem item) {
        try {
            inventoryItemRepository.saveAndFlush(item);
            entityManager.clear();
            return true;
        } catch (DataIntegrityViolationException ex) {
            entityManager.clear();
            return false;
        }
    }

    /**
     * Persists a batch of {@link ProcessingError} entities in an isolated transaction.
     *
     * @param errors list of error entities to save
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveErrorBatch(List<ProcessingError> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }
        log.debug("Persisting batch of {} processing errors", errors.size());
        processingErrorRepository.saveAll(errors);
        processingErrorRepository.flush();
        entityManager.clear();
    }
}
