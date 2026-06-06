package com.ecommerce.inventory.dto;

/** Ask inventory-service to reserve {quantity} units of {skuCode}. */
public record ReserveRequest(
        String skuCode,
        Integer quantity
) {
}
