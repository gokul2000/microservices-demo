package com.ecommerce.order.dto;

import com.ecommerce.order.model.Order;

public record OrderResponse(
        Long id,
        String orderNumber,
        String skuCode,
        Integer quantity,
        String status,
        Long reservationId
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getSkuCode(),
                order.getQuantity(),
                order.getStatus().name(),
                order.getReservationId()
        );
    }
}
