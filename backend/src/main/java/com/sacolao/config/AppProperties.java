package com.sacolao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String frontendDir,
        String publicFrontendUrl,
        Cors cors,
        Jwt jwt,
        Auth auth,
        Bootstrap bootstrap,
        Payments payments,
        Security security,
        Cloudinary cloudinary,
        Resend resend
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

    public record Cloudinary(
            String cloudName,
            String apiKey,
            String apiSecret,
            String url,
            String folder,
            long maxBytes
    ) {
    }

    public record Resend(
            String apiKey,
            String from
    ) {
    }
}
