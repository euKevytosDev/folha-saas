package com.sacolao.security;

import com.sacolao.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern ORDER = Pattern.compile("^/api/v1/store/[^/]+/orders$");
    private static final Pattern QUOTE = Pattern.compile("^/api/v1/store/[^/]+/cart/quote$");
    private static final Pattern CATALOG = Pattern.compile("^/api/v1/store/[^/]+/catalog$");

    private final AppProperties properties;
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public PublicRateLimitFilter(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        Limit limit = limitFor(request.getMethod(), request.getRequestURI());
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!allow(limit.name() + ":" + clientIp(request), limit.max(), limit.windowSeconds())) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(limit.windowSeconds()));
            response.getWriter().write("""
                    {"status":429,"code":"RATE_LIMITED","message":"Muitas requisições agora. Espere um pouco e tente de novo.","errors":[]}
                    """);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Limit limitFor(String method, String uri) {
        var rate = properties.security().rateLimit();
        if ("POST".equalsIgnoreCase(method) && ORDER.matcher(uri).matches()) {
            return new Limit("order", rate.publicOrderMax(), rate.publicOrderWindowSeconds());
        }
        if ("POST".equalsIgnoreCase(method) && QUOTE.matcher(uri).matches()) {
            return new Limit("quote", rate.publicQuoteMax(), rate.publicQuoteWindowSeconds());
        }
        if ("GET".equalsIgnoreCase(method) && CATALOG.matcher(uri).matches()) {
            return new Limit("catalog", rate.publicCatalogMax(), rate.publicCatalogWindowSeconds());
        }
        return null;
    }

    private boolean allow(String key, int max, int windowSeconds) {
        if (max <= 0 || windowSeconds <= 0) {
            return true;
        }
        long now = Instant.now().toEpochMilli();
        long windowMs = windowSeconds * 1000L;
        Deque<Long> timestamps = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= max) {
                return false;
            }
            timestamps.addLast(now);
        }
        if (buckets.size() > 20_000) {
            buckets.clear();
        }
        return true;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Limit(String name, int max, int windowSeconds) {
    }
}
