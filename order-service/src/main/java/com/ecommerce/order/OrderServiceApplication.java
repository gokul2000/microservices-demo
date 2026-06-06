package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Order Service — orchestrates order placement.
 *
 * Before saving an order it calls inventory-service to confirm the SKU is in
 * stock. @EnableFeignClients activates the declarative HTTP clients in the
 * "client" package, which resolve "inventory-service" through Eureka and
 * load-balance across its instances — no hardcoded URLs.
 */
@EnableFeignClients
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
