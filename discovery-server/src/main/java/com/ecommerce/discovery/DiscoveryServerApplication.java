package com.ecommerce.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server — the service registry.
 *
 * Every other microservice registers itself here on startup and looks up the
 * network locations of its peers from here. @EnableEurekaServer turns this plain
 * Spring Boot app into a discovery server. Clients talk to it on port 8761.
 */
@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
