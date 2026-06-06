package com.ecommerce.order.service;

import com.ecommerce.order.client.InventoryClient;
import com.ecommerce.order.client.ReserveRequest;
import com.ecommerce.order.client.ReserveResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * All network hops to inventory-service go through here, behind Resilience4j.
 *
 * This lives in its OWN bean on purpose. Resilience4j works via Spring AOP
 * proxies, which only intercept calls coming from *outside* the bean. A method
 * calling an annotated method on {@code this} bypasses the proxy and the breaker
 * silently never runs. Injecting this gateway into the saga makes calls cross
 * the proxy boundary, so the aspects apply.
 *
 * Both saga operations are guarded:
 *   - @Retry          a few attempts for transient failures, then give up.
 *   - @CircuitBreaker once the failure rate crosses the threshold the breaker
 *                     OPENS and short-circuits to the fallback for a cooldown
 *                     window, so we stop hammering a service that's already down.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryGateway {

    private final InventoryClient inventoryClient;

    // --- Forward action -------------------------------------------------------

    @Retry(name = "inventory")
    @CircuitBreaker(name = "inventory", fallbackMethod = "reserveFallback")
    public ReserveResponse reserveStock(String skuCode, int quantity) {
        return inventoryClient.reserve(new ReserveRequest(skuCode, quantity));
    }

    @SuppressWarnings("unused")
    private ReserveResponse reserveFallback(String skuCode, int quantity, Throwable t) {
        log.warn("Reserve for sku '{}' fell back (circuit open or call failed): {}", skuCode, t.toString());
        throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                "Inventory service is currently unavailable, please try again shortly");
    }

    // --- Compensation ---------------------------------------------------------

    @Retry(name = "inventory")
    @CircuitBreaker(name = "inventory", fallbackMethod = "releaseFallback")
    public void releaseStock(Long reservationId) {
        inventoryClient.release(reservationId);
    }

    /**
     * If even the compensation can't reach inventory-service, we surface it so the
     * saga can log a "compensation failed" alert. In production this is where you'd
     * enqueue the release for guaranteed later retry (a dead-letter / outbox), since
     * a stuck reservation leaks stock until it's undone.
     */
    @SuppressWarnings("unused")
    private void releaseFallback(Long reservationId, Throwable t) {
        log.error("RELEASE compensation for reservation {} fell back: {}", reservationId, t.toString());
        throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                "Could not release reservation " + reservationId);
    }
}
