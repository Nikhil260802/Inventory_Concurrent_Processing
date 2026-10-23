package Inventory_Concurrent_Processing.controller;

import Inventory_Concurrent_Processing.dto.ProcessingReportDto;
import Inventory_Concurrent_Processing.dto.inventory.InventoryItemRequestDto;
import Inventory_Concurrent_Processing.dto.inventory.InventoryItemResponseDto;
import Inventory_Concurrent_Processing.enums.JobStatus;
import Inventory_Concurrent_Processing.enums.ProcessingMode;
import Inventory_Concurrent_Processing.service.inventory.InventoryCrudService;
import Inventory_Concurrent_Processing.service.processing.InventoryProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private InventoryCrudService crudService;

    @Mock
    private InventoryProcessingService processingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        JobController jobController = new JobController(processingService);
        InventoryController inventoryController = new InventoryController(crudService, jobController);

        mockMvc = MockMvcBuilders.standaloneSetup(inventoryController, jobController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/inventory - should create inventory item when valid")
    void testCreateInventoryItem() throws Exception {
        InventoryItemRequestDto request = InventoryItemRequestDto.builder()
                .code("MED-0001")
                .name("Paracetamol 500mg")
                .batchNumber("B101")
                .quantity(100)
                .price(BigDecimal.valueOf(12.50))
                .expiryDate(LocalDate.of(2027, 12, 31))
                .build();

        InventoryItemResponseDto response = InventoryItemResponseDto.builder()
                .id(1L)
                .code(request.getCode())
                .name(request.getName())
                .batchNumber(request.getBatchNumber())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .expiryDate(request.getExpiryDate())
                .build();

        when(crudService.create(any(InventoryItemRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.code").value("MED-0001"))
                .andExpect(jsonPath("$.name").value("Paracetamol 500mg"))
                .andExpect(jsonPath("$.quantity").value(100));
    }

    @Test
    @DisplayName("POST /api/v1/inventory - should reject negative quantity")
    void testRejectNegativeQuantity() throws Exception {
        InventoryItemRequestDto request = InventoryItemRequestDto.builder()
                .code("MED-0001")
                .name("Paracetamol 500mg")
                .batchNumber("B101")
                .quantity(-5) // INVALID
                .price(BigDecimal.valueOf(12.50))
                .expiryDate(LocalDate.of(2027, 12, 31))
                .build();

        mockMvc.perform(post("/api/v1/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0]").value("quantity: quantity must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("GET /api/v1/inventory - should return paginated list")
    void testSearchInventory() throws Exception {
        InventoryItemResponseDto item = InventoryItemResponseDto.builder()
                .id(1L)
                .code("MED-0001")
                .name("Paracetamol")
                .batchNumber("B101")
                .quantity(50)
                .price(BigDecimal.valueOf(15.00))
                .expiryDate(LocalDate.of(2028, 1, 1))
                .build();

        when(crudService.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/inventory")
                        .param("name", "Paracetamol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("MED-0001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{jobId} - should return job report")
    void testGetJobStatus() throws Exception {
        ProcessingReportDto report = ProcessingReportDto.builder()
                .jobId(10L)
                .fileName("test.csv")
                .processingMode(ProcessingMode.PARALLEL)
                .status(JobStatus.COMPLETED)
                .totalRecords(100L)
                .successfulRecords(95L)
                .failedRecords(3L)
                .duplicateRecords(2L)
                .executionTimeMs(150L)
                .throughputRecordsPerSec(BigDecimal.valueOf(666.67))
                .build();

        when(processingService.getJobReport(10L)).thenReturn(report);

        mockMvc.perform(get("/api/v1/jobs/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(10))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.successfulRecords").value(95));
    }
}
