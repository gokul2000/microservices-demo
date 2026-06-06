package com.ecommerce.inventory;

import com.ecommerce.inventory.model.Inventory;
import com.ecommerce.inventory.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Inventory Service — answers "is SKU X in stock?".
 *
 * The order-service calls this service (via OpenFeign, through the Eureka
 * load balancer) before confirming an order. Like every business service it is
 * a Eureka client and pulls its config from the config server.
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    /** Seed a little demo stock so /api/inventory returns something useful. */
    @Bean
    public CommandLineRunner seedData(InventoryRepository repository) {
        return args -> repository.saveAll(List.of(
                Inventory.builder().skuCode("iphone-15").quantity(50).build(),
                Inventory.builder().skuCode("galaxy-s24").quantity(0).build(),
                Inventory.builder().skuCode("pixel-9").quantity(12).build()
        ));
    }
}
