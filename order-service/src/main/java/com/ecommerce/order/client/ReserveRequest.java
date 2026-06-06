package com.ecommerce.order.client;

/** Payload sent to inventory-service's /reserve endpoint. */
public record ReserveRequest(
        String skuCode,
        Integer quantity
) {
}
