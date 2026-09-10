package com.codelens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "codelens")
public record CodeLensProperties(Path reposDir, Ai ai, Analysis analysis, Cors cors) {

    public record Ai(String baseUrl, Duration timeout, boolean enabled) {}

    public record Analysis(int parallelism, long maxFileBytes) {}

    public record Cors(List<String> allowedOrigins) {}
}
