package com.sacolao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String frontendDir,
        Cors cors,
        Jwt jwt,
        Auth auth,
        Bootstrap bootstrap,
        Payments payments,
        Security security
) {
    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(
            String secret,
            long accessTokenExpirationMs,
            long refreshTokenExpirationMs
    ) {
    }

    public record Auth(
            long passwordResetExpirationMs,
            boolean cookieSecure
    ) {
    }

    public record Bootstrap(
            String superadminEmail,
            String superadminPassword
    ) {
    }

    public record Payments(
            boolean simulateEnabled,
            boolean mockWebhooksEnabled,
            boolean requireWebhookSecret,
            String mockWebhookSecret
    ) {
    }

    public record Security(RateLimit rateLimit) {
        public record RateLimit(
                int loginMaxAttempts,
                int loginWindowSeconds,
                int registerMaxAttempts,
                int registerWindowSeconds
        ) {
        }
    }
}
