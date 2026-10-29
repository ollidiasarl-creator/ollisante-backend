package com.ollisante.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollisante.security.jwt")
public record JwtProperties(String secret, long accessTtlMinutes, long refreshTtlDays) {
}
