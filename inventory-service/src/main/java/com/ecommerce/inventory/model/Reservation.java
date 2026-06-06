package com.ecommerce.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A unit of reserved stock created during the "reserve" step of the order saga.
 *
 * Tracking reservations by id (rather than just decrementing a number) lets the
 * compensation be precise and idempotent: releasing the same reservation twice
 * is a safe no-op, because we flip its status to RELEASED the first time.
 */
@Entity
@Table(name = "reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String skuCode;

    private Integer quantity;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
}
