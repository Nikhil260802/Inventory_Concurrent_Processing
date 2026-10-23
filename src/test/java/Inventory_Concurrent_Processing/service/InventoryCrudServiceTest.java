package Inventory_Concurrent_Processing.service;

import Inventory_Concurrent_Processing.dto.inventory.InventoryItemRequestDto;
import Inventory_Concurrent_Processing.dto.inventory.InventoryItemResponseDto;
import Inventory_Concurrent_Processing.entity.InventoryItem;
import Inventory_Concurrent_Processing.exception.DuplicateResourceException;
import Inventory_Concurrent_Processing.exception.ResourceNotFoundException;
import Inventory_Concurrent_Processing.repository.InventoryItemRepository;
import Inventory_Concurrent_Processing.service.inventory.InventoryCrudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryCrudServiceTest {

    @Mock
    private InventoryItemRepository repository;

    private InventoryCrudService crudService;

    @BeforeEach
    void setUp() {
        crudService = new InventoryCrudService(repository);
    }

    @Test
    @DisplayName("create - should save and return DTO when unique")
    void testCreateSuccess() {
        InventoryItemRequestDto request = InventoryItemRequestDto.builder()
                .code("MED-100")
                .name("Paracetamol")
                .batchNumber("B100")
                .quantity(50)
                .price(BigDecimal.valueOf(10.00))
                .expiryDate(LocalDate.of(2027, 5, 20))
                .build();

        when(repository.existsByCodeAndBatchNumber("MED-100", "B100")).thenReturn(false);
        when(repository.save(any(InventoryItem.class))).thenAnswer(inv -> {
            InventoryItem item = inv.getArgument(0);
            item.setId(1L);
            return item;
        });

        InventoryItemResponseDto result = crudService.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("MED-100");
        assertThat(result.getName()).isEqualTo("Paracetamol");
        assertThat(result.getQuantity()).isEqualTo(50);
    }

    @Test
    @DisplayName("create - should throw DuplicateResourceException when composite key exists")
    void testCreateDuplicateThrows() {
        InventoryItemRequestDto request = InventoryItemRequestDto.builder()
                .code("MED-100")
                .name("Paracetamol")
                .batchNumber("B100")
                .quantity(50)
                .price(BigDecimal.valueOf(10.00))
                .expiryDate(LocalDate.of(2027, 5, 20))
                .build();

        when(repository.existsByCodeAndBatchNumber("MED-100", "B100")).thenReturn(true);

        assertThatThrownBy(() -> crudService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("getById - should throw ResourceNotFoundException when item does not exist")
    void testGetByIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crudService.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found with ID: 999");
    }

    @Test
    @DisplayName("delete - should throw ResourceNotFoundException when deleting non-existent ID")
    void testDeleteNotFound() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> crudService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found with ID: 999");
    }

    @Test
    @DisplayName("search - should search by query across fields")
    void testSearchByQuery() {
        InventoryItem item = InventoryItem.builder()
                .id(1L).code("MED-001").name("Amoxicillin").batchNumber("B1").quantity(10).price(BigDecimal.ONE).expiryDate(LocalDate.now()).build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(repository.searchByAnyField("Amox", "Amox", "Amox", pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        Page<InventoryItemResponseDto> result = crudService.search("Amox", null, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Amoxicillin");
    }
}
