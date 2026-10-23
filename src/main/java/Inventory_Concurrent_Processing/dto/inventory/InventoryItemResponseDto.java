package Inventory_Concurrent_Processing.dto.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response payload representing an inventory item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemResponseDto {

    private Long id;
    private String code;
    private String name;
    private String batchNumber;
    private Integer quantity;
    private BigDecimal price;
    private LocalDate expiryDate;
    private Long sourceJobId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
