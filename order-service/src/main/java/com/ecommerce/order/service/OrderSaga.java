package com.ecommerce.order.service;

import com.ecommerce.order.client.ReserveResponse;
import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Orchestration-based Saga for placing an order.
 *
 * A saga replaces the single ACID transaction you'd have in a monolith (which is
 * impossible across services with separate databases) with a SEQUENCE of local
 * transactions, each paired with a COMPENSATING action that semantically undoes
 * it. If any forward step fails, the already-completed steps are compensated in
 * reverse order. The system reaches a consistent end state (everything done, or
 * everything undone) — "eventual consistency" rather than atomic rollback.
 *
 * Steps here:
 *   1. Reserve inventory   (remote, mutating)   -> compensate: release reservation
 *   2. Create order PENDING (local, mutating)   -> compensate: mark order CANCELLED
 *   3. Confirm order        (can fail)           -> no compensation; it's the last step
 *
 * NOTE: deliberately NOT @Transactional. A local transaction can't span the
 * remote calls, and we WANT each step's effect (e.g. the PENDING row) to be
 * visible/committed so compensation has something concrete to undo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSaga {

    private final InventoryGateway inventoryGateway;
    private final OrderRepository orderRepository;

    /** One undo action plus a human-readable label, pushed onto a LIFO stack. */
    private record Compensation(String description, Runnable action) {
    }

    public OrderResponse placeOrder(OrderRequest request) {
        // Undo actions accumulate here; on failure we pop them newest-first.
        Deque<Compensation> compensations = new ArrayDeque<>();
        String sagaId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[saga {}] START sku={} qty={} simulateFailure={}",
                sagaId, request.skuCode(), request.quantity(), request.simulateFailure());

        try {
            // ---- Step 1: reserve inventory (remote) -------------------------
            ReserveResponse reservation = inventoryGateway.reserveStock(request.skuCode(), request.quantity());
            if (!reservation.success()) {
                // Nothing has changed yet, so just abort — no compensation needed.
                throw new ResponseStatusException(CONFLICT, reservation.message());
            }
            compensations.push(new Compensation(
                    "release reservation " + reservation.reservationId(),
                    () -> inventoryGateway.releaseStock(reservation.reservationId())));
            log.info("[saga {}] step 1 OK  reserved (reservationId={})", sagaId, reservation.reservationId());

            // ---- Step 2: create the order as PENDING (local) ----------------
            Order order = orderRepository.save(Order.builder()
                    .orderNumber(UUID.randomUUID().toString())
                    .skuCode(request.skuCode())
                    .quantity(request.quantity())
                    .reservationId(reservation.reservationId())
                    .status(OrderStatus.PENDING)
                    .build());
            compensations.push(new Compensation(
                    "cancel order " + order.getId(),
                    () -> {
                        order.setStatus(OrderStatus.CANCELLED);
                        orderRepository.save(order);
                    }));
            log.info("[saga {}] step 2 OK  order {} PENDING", sagaId, order.getId());

            // ---- Step 3: confirm the order (can fail) -----------------------
            confirmOrder(request, sagaId);

            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            log.info("[saga {}] step 3 OK  order {} CONFIRMED — saga complete", sagaId, order.getId());
            return OrderResponse.from(order);

        } catch (RuntimeException failure) {
            log.warn("[saga {}] FAILED: {} — running {} compensation(s)",
                    sagaId, failure.getMessage(), compensations.size());
            compensate(compensations, sagaId);
            throw failure;
        }
    }

    /**
     * Final step. In a real system this might call a shipping/risk service; here
     * it's a deterministic, demo-triggerable failure so you can exercise the
     * rollback path on demand via {@code "simulateFailure": true}.
     */
    private void confirmOrder(OrderRequest request, String sagaId) {
        if (request.simulateFailure()) {
            log.info("[saga {}] step 3 simulated failure during confirmation", sagaId);
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY,
                    "Order confirmation failed (simulated) — saga will roll back");
        }
    }

    /**
     * Run compensations newest-first (LIFO). Each is isolated: one failing
     * compensation is logged but does not stop the others from running.
     */
    private void compensate(Deque<Compensation> compensations, String sagaId) {
        while (!compensations.isEmpty()) {
            Compensation c = compensations.pop();
            try {
                log.info("[saga {}] compensating: {}", sagaId, c.description());
                c.action().run();
            } catch (RuntimeException ex) {
                log.error("[saga {}] compensation FAILED: {} — {}", sagaId, c.description(), ex.toString());
            }
        }
    }
}
