package Inventory_Concurrent_Processing.service.inventory;

import Inventory_Concurrent_Processing.dto.inventory.InventoryItemRequestDto;
import Inventory_Concurrent_Processing.dto.inventory.InventoryItemResponseDto;
import Inventory_Concurrent_Processing.entity.InventoryItem;
import Inventory_Concurrent_Processing.exception.DuplicateResourceException;
import Inventory_Concurrent_Processing.exception.ResourceNotFoundException;
import Inventory_Concurrent_Processing.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service providing CRUD operations and search functionality for Inventory records.
 */
@Service
public class InventoryCrudService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCrudService.class);

    private final InventoryItemRepository repository;

    public InventoryCrudService(InventoryItemRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new inventory record with business key uniqueness enforcement.
     */
    @Transactional
    public InventoryItemResponseDto create(InventoryItemRequestDto request) {
        if (repository.existsByCodeAndBatchNumber(request.getCode().trim(), request.getBatchNumber().trim())) {
            throw new DuplicateResourceException("Inventory item with code '" + request.getCode()
                    + "' and batchNumber '" + request.getBatchNumber() + "' already exists");
        }

        InventoryItem item = InventoryItem.builder()
                .code(request.getCode().trim())
                .name(request.getName().trim())
                .batchNumber(request.getBatchNumber().trim())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .expiryDate(request.getExpiryDate())
                .build();

        InventoryItem saved = repository.save(item);
        log.info("Created inventory item with ID: {}", saved.getId());
        return toDto(saved);
    }

    /**
     * Retrieves an inventory item by primary key.
     */
    @Transactional(readOnly = true)
    public InventoryItemResponseDto getById(Long id) {
        InventoryItem item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found with ID: " + id));
        return toDto(item);
    }

    /**
     * Updates an existing inventory item with validation.
     */
    @Transactional
    public InventoryItemResponseDto update(Long id, InventoryItemRequestDto request) {
        InventoryItem item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found with ID: " + id));

        String newCode = request.getCode().trim();
        String newBatch = request.getBatchNumber().trim();

        // Check if updating code/batchNumber creates a collision with another item
        if (!item.getCode().equalsIgnoreCase(newCode) || !item.getBatchNumber().equalsIgnoreCase(newBatch)) {
            Optional<InventoryItem> collision = repository.findByCodeAndBatchNumber(newCode, newBatch);
            if (collision.isPresent() && !collision.get().getId().equals(id)) {
                throw new DuplicateResourceException("Cannot update: item with code '" + newCode
                        + "' and batchNumber '" + newBatch + "' already exists");
            }
        }

        item.setCode(newCode);
        item.setName(request.getName().trim());
        item.setBatchNumber(newBatch);
        item.setQuantity(request.getQuantity());
        item.setPrice(request.getPrice());
        item.setExpiryDate(request.getExpiryDate());

        InventoryItem updated = repository.save(item);
        log.info("Updated inventory item with ID: {}", updated.getId());
        return toDto(updated);
    }

    /**
     * Deletes an inventory item by primary key.
     */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Inventory item not found with ID: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted inventory item with ID: {}", id);
    }

    /**
     * Flexible search supporting:
     * 1) Combined search across code, name, and batchNumber via 'query'
     * 2) Specific filters by 'name', 'code', or 'batchNumber'
     * 3) Full pagination and sorting
     */
    @Transactional(readOnly = true)
    public Page<InventoryItemResponseDto> search(
            String query,
            String code,
            String name,
            String batchNumber,
            Pageable pageable) {

        Page<InventoryItem> items;

        if (query != null && !query.isBlank()) {
            items = repository.searchByAnyField(query.trim(), query.trim(), query.trim(), pageable);
        } else if (code != null && !code.isBlank()) {
            items = repository.findByCodeContainingIgnoreCase(code.trim(), pageable);
        } else if (name != null && !name.isBlank()) {
            items = repository.findByNameContainingIgnoreCase(name.trim(), pageable);
        } else if (batchNumber != null && !batchNumber.isBlank()) {
            items = repository.findByBatchNumberContainingIgnoreCase(batchNumber.trim(), pageable);
        } else {
            items = repository.findAll(pageable);
        }

        return items.map(this::toDto);
    }

    private InventoryItemResponseDto toDto(InventoryItem item) {
        return InventoryItemResponseDto.builder()
                .id(item.getId())
                .code(item.getCode())
                .name(item.getName())
                .batchNumber(item.getBatchNumber())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .expiryDate(item.getExpiryDate())
                .sourceJobId(item.getSourceJobId())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
