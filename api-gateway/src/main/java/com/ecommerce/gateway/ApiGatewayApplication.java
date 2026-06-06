package com.ecommerce.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway — the single front door for every client request.
 *
 * Clients never call product/inventory/order services directly. They hit the
 * gateway (port 8080), which:
 *   - matches the request path against route predicates,
 *   - looks the target service up in Eureka,
 *   - load-balances across that service's instances (lb:// URIs), and
 *   - forwards the request.
 *
 * This centralizes cross-cutting concerns (routing, CORS, auth, rate limiting)
 * and hides the internal topology from clients. Routes are defined in the
 * config server's api-gateway.yml.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
