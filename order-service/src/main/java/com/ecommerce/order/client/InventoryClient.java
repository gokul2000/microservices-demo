package com.ecommerce.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative HTTP client for inventory-service.
 *
 * The `name` is the Eureka-registered service id — NOT a host/port. At call
 * time Spring Cloud LoadBalancer resolves it to a live instance.
 *
 * For the saga it exposes the forward action (reserve) and its compensation
 * (release), in addition to the simple stock query.
 */
@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @GetMapping("/api/inventory/{skuCode}")
    InventoryResponse isInStock(@PathVariable String skuCode);

    @PostMapping("/api/inventory/reserve")
    ReserveResponse reserve(@RequestBody ReserveRequest request);

    @PostMapping("/api/inventory/release/{reservationId}")
    void release(@PathVariable Long reservationId);
}
