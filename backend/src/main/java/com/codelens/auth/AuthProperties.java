package com.codelens.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "codelens.auth")
public record AuthProperties(String jwtSecret, @DefaultValue("7d") Duration tokenTtl) {
}
