package com.sacolao.security;

import com.sacolao.common.exception.TooManyRequestsException;
import com.sacolao.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {

    private final AppProperties properties;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public AuthRateLimiter(AppProperties properties) {
        this.properties = properties;
    }

    public void checkLogin(String clientKey) {
        var limit = properties.security().rateLimit();
        check("login:" + normalize(clientKey), limit.loginMaxAttempts(), limit.loginWindowSeconds());
    }

    public void checkRegister(String clientKey) {
        var limit = properties.security().rateLimit();
        check("register:" + normalize(clientKey), limit.registerMaxAttempts(), limit.registerWindowSeconds());
    }

    public void checkForgotPassword(String clientKey) {
        var limit = properties.security().rateLimit();
        check("forgot:" + normalize(clientKey), limit.loginMaxAttempts(), limit.loginWindowSeconds());
    }

    private void check(String key, int maxAttempts, int windowSeconds) {
        long now = Instant.now().toEpochMilli();
        long windowMs = windowSeconds * 1000L;
        Deque<Long> timestamps = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                throw new TooManyRequestsException("Muitas tentativas. Aguarde alguns minutos e tente de novo.");
            }
            timestamps.addLast(now);
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }
}
