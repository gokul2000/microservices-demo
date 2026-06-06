package com.ecommerce.order.model;

public enum OrderStatus {
    /** Created, inventory reserved, not yet confirmed. */
    PENDING,
    /** All saga steps succeeded. */
    CONFIRMED,
    /** A later step failed; the saga compensated this order. */
    CANCELLED
}
