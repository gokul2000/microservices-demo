package com.ecommerce.product.dto;

import com.ecommerce.product.model.Product;

import java.math.BigDecimal;

/** Outgoing representation of a product. */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String skuCode
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getSkuCode()
        );
    }
}
