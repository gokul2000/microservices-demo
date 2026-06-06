package com.ecommerce.inventory.controller;

import com.ecommerce.inventory.dto.InventoryResponse;
import com.ecommerce.inventory.dto.ReserveRequest;
import com.ecommerce.inventory.dto.ReserveResponse;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reachable through the gateway at /api/inventory/**.
 *
 * The reserve/release pair are the saga endpoints the order-service drives:
 *   - POST /reserve            forward action (compensatable)
 *   - POST /release/{id}       compensation (undo)
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{skuCode}")
    public InventoryResponse isInStock(@PathVariable String skuCode) {
        return inventoryService.isInStock(skuCode);
    }

    @PostMapping("/reserve")
    public ReserveResponse reserve(@RequestBody ReserveRequest request) {
        return inventoryService.reserve(request.skuCode(), request.quantity());
    }

    @PostMapping("/release/{reservationId}")
    public void release(@PathVariable Long reservationId) {
        inventoryService.release(reservationId);
    }
}
