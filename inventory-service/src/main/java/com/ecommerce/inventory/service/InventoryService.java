package com.ecommerce.inventory.service;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.ReserveResponse;
import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.model.Reservation;
import com.ecommerce.inventory.model.ReservationStatus;
import com.ecommerce.inventory.repository.InventoryRepository;
import com.ecommerce.inventory.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public InventoryResponse isInStock(String skuCode) {
        return inventoryRepository.findBySkuCode(skuCode)
                .map(inv -> new InventoryResponse(
                        inv.getSkuCode(),
                        inv.getQuantity() != null && inv.getQuantity() > 0,
                        inv.getQuantity()))
                .orElse(new InventoryResponse(skuCode, false, 0));
    }

    /**
     * SAGA STEP (forward action). Atomically decrements available stock and
     * records a Reservation. If there isn't enough stock we reserve nothing and
     * report failure — the saga will abort before any compensation is needed.
     */
    @Transactional
    public ReserveResponse reserve(String skuCode, int quantity) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode).orElse(null);

        if (inventory == null || inventory.getQuantity() == null || inventory.getQuantity() < quantity) {
            int available = inventory == null || inventory.getQuantity() == null ? 0 : inventory.getQuantity();
            log.info("RESERVE failed for sku={} qty={} (available={})", skuCode, quantity, available);
            return new ReserveResponse(null, skuCode, quantity, false,
                    "Insufficient stock (available=" + available + ")");
        }

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventoryRepository.save(inventory);

        Reservation reservation = reservationRepository.save(Reservation.builder()
                .skuCode(skuCode)
                .quantity(quantity)
                .status(ReservationStatus.RESERVED)
                .build());

        log.info("RESERVE ok    sku={} qty={} reservationId={} (remaining={})",
                skuCode, quantity, reservation.getId(), inventory.getQuantity());
        return new ReserveResponse(reservation.getId(), skuCode, quantity, true, "Reserved");
    }

    /**
     * SAGA COMPENSATION (undo). Adds the reserved quantity back to stock.
     * Idempotent: an unknown id or an already-RELEASED reservation is a no-op,
     * so retried/duplicated compensation calls can't double-credit stock.
     */
    @Transactional
    public void release(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("RELEASE skipped: reservation {} not found", reservationId);
            return;
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            log.info("RELEASE no-op: reservation {} already released", reservationId);
            return;
        }

        Inventory inventory = inventoryRepository.findBySkuCode(reservation.getSkuCode()).orElseThrow();
        inventory.setQuantity(inventory.getQuantity() + reservation.getQuantity());
        inventoryRepository.save(inventory);

        reservation.setStatus(ReservationStatus.RELEASED);
        reservationRepository.save(reservation);

        log.info("RELEASE ok    reservationId={} sku={} qty={} (restored to {})",
                reservationId, reservation.getSkuCode(), reservation.getQuantity(), inventory.getQuantity());
    }
}
