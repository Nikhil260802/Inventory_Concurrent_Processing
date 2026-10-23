package Inventory_Concurrent_Processing.dto.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for creating or updating an inventory item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemRequestDto {

    @NotBlank(message = "code is mandatory and cannot be blank")
    @Size(max = 100, message = "code must not exceed 100 characters")
    private String code;

    @NotBlank(message = "name is mandatory and cannot be blank")
    @Size(max = 255, message = "name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "batchNumber is mandatory and cannot be blank")
    @Size(max = 100, message = "batchNumber must not exceed 100 characters")
    private String batchNumber;

    @NotNull(message = "quantity is mandatory")
    @Min(value = 0, message = "quantity must be greater than or equal to 0")
    private Integer quantity;

    @NotNull(message = "price is mandatory")
    @DecimalMin(value = "0.01", message = "price must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimal places")
    private BigDecimal price;

    @NotNull(message = "expiryDate is mandatory")
    private LocalDate expiryDate;
}
