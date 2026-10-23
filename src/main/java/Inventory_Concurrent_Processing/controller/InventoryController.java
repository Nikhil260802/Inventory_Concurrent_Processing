package Inventory_Concurrent_Processing.controller;

import Inventory_Concurrent_Processing.dto.ProcessingReportDto;
import Inventory_Concurrent_Processing.dto.inventory.InventoryItemRequestDto;
import Inventory_Concurrent_Processing.dto.inventory.InventoryItemResponseDto;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import Inventory_Concurrent_Processing.service.inventory.InventoryCrudService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST API controller for inventory management, search, and upload endpoints.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryCrudService crudService;
    private final JobController jobController;

    public InventoryController(InventoryCrudService crudService, JobController jobController) {
        this.crudService = crudService;
        this.jobController = jobController;
    }

    /**
     * Upload endpoint alias under /api/v1/inventory/upload for convenience.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessingReportDto> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "PARALLEL") ProcessingMode mode) throws IOException {
        return jobController.uploadCsv(file, mode);
    }

    /**
     * Search and list inventory items with pagination and sorting.
     *
     * @param query       general search term across code, name, and batchNumber
     * @param code        filter by code (partial, case-insensitive)
     * @param name        filter by name (partial, case-insensitive)
     * @param batchNumber filter by batchNumber (partial, case-insensitive)
     * @param pageable    page and sort instructions
     * @return paginated items
     */
    @GetMapping
    public ResponseEntity<Page<InventoryItemResponseDto>> searchInventory(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "batchNumber", required = false) String batchNumber,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {

        Page<InventoryItemResponseDto> result = crudService.search(query, code, name, batchNumber, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves an inventory record by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InventoryItemResponseDto> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(crudService.getById(id));
    }

    /**
     * Creates a new inventory record with validation and duplicate prevention.
     */
    @PostMapping
    public ResponseEntity<InventoryItemResponseDto> create(@Valid @RequestBody InventoryItemRequestDto request) {
        InventoryItemResponseDto response = crudService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates an existing inventory item.
     */
    @PutMapping("/{id}")
    public ResponseEntity<InventoryItemResponseDto> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody InventoryItemRequestDto request) {
        return ResponseEntity.ok(crudService.update(id, request));
    }

    /**
     * Deletes an inventory record by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        crudService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
