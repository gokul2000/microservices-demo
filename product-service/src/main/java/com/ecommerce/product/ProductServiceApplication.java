package com.ecommerce.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Product Service — owns the product catalog.
 *
 * A Eureka client: it registers itself with the discovery server on startup so
 * the gateway and other services can find it by name ("product-service")
 * without knowing its host/port. Its config (port, datasource) is fetched from
 * the config server.
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
