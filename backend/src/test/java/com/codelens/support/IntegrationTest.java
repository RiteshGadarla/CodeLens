package com.codelens.support;

import com.codelens.auth.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// one postgres container per JVM
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "management.health.redis.enabled=false",
        "codelens.ai.enabled=false"
})
public abstract class IntegrationTest {

    // service-level tests bypass the web layer and its ownership checks
    protected static final Long NO_OWNER = null;

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected AuthService auth;
    @Autowired
    protected WebApplicationContext context;

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

    protected AuthService.Session signUp(String name) {
        return auth.register(name, name + "-" + UUID.randomUUID() + "@example.com", "correct-horse");
    }

    // every request carries the token
    protected MockMvc mvcAs(String token) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
    }
}
