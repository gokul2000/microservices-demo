package com.ecommerce.inventory.dto;

/**
 * Outcome of a reserve attempt. {@code reservationId} is non-null only when
 * {@code success} is true — the order saga keeps it so it can later release.
 */
public record ReserveResponse(
        Long reservationId,
        String skuCode,
        Integer quantity,
        boolean success,
        String message
) {
}
