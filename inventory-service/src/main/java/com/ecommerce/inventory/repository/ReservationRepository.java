package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
