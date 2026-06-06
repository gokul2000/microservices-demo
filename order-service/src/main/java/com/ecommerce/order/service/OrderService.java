package com.ecommerce.order.service;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Thin application service: placing an order is a distributed transaction, so it
 * delegates the orchestration (and compensation) to {@link OrderSaga}.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderSaga orderSaga;

    public OrderResponse placeOrder(OrderRequest request) {
        return orderSaga.placeOrder(request);
    }
}
