package com.ecommerce.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server — the single source of truth for configuration.
 *
 * Instead of every service shipping its own application.yml, services ask this
 * server for their config at startup. The server reads property files from a
 * backing repository (here: a local "config-repo" folder via the "native"
 * profile; in production this is typically a Git repo). @EnableConfigServer
 * exposes the HTTP endpoints clients use, e.g. GET /{application}/{profile}.
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
