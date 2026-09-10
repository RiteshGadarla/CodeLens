package com.codelens.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

// one postgres container per JVM
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "codelens.ai.enabled=false"
})
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("codelens.repos-dir", () -> {
            try {
                return Files.createTempDirectory("codelens-it").toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
