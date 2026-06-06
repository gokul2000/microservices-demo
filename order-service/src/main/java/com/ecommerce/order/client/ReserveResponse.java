package com.ecommerce.order.client;

/** Mirror of inventory-service's reserve outcome, for deserializing Feign calls. */
public record ReserveResponse(
        Long reservationId,
        String skuCode,
        Integer quantity,
        boolean success,
        String message
) {
}
