package com.sacolao.security;

import com.sacolao.config.AppProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieService {

    public static final String REFRESH_COOKIE = "folha_refresh";

    private final AppProperties properties;

    public AuthCookieService(AppProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie createRefreshCookie(String rawToken) {
        return build(rawToken, Duration.ofMillis(properties.jwt().refreshTokenExpirationMs()));
    }

    public ResponseCookie clearRefreshCookie() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        boolean secure = properties.auth().cookieSecure();
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secure)
                // None permite o Pages (outro site) reaproveitar o mesmo login.
                .sameSite(secure ? "None" : "Lax")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
