package com.ollisante.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollisante.otp")
public record OtpProperties(int codeTtlMinutes, int maxAttempts, int resendSeconds, int dailyLimit) {
}
