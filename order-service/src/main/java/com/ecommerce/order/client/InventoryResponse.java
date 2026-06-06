package com.ecommerce.order.client;

/** Mirror of inventory-service's response payload, used to deserialize Feign calls. */
public record InventoryResponse(
        String skuCode,
        boolean inStock,
        Integer quantity
) {
}
