package com.ecommerce.inventory.dto;

/** Tells the caller whether a SKU is in stock (and how much). */
public record InventoryResponse(
        String skuCode,
        boolean inStock,
        Integer quantity
) {
}
