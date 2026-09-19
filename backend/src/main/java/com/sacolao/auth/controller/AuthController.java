package com.sacolao.auth.controller;

import com.sacolao.auth.dto.AuthResponse;
import com.sacolao.auth.dto.ChangePasswordRequest;
import com.sacolao.auth.dto.ForgotPasswordRequest;
import com.sacolao.auth.dto.LoginRequest;
import com.sacolao.auth.dto.MeResponse;
import com.sacolao.auth.dto.MessageResponse;
import com.sacolao.auth.dto.RefreshRequest;
import com.sacolao.auth.dto.RegisterRequest;
import com.sacolao.auth.dto.ResetPasswordRequest;
import com.sacolao.auth.service.AuthService;
import com.sacolao.security.AuthCookieService;
import com.sacolao.security.AuthRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final AuthRateLimiter authRateLimiter;

    public AuthController(
            AuthService authService,
            AuthCookieService authCookieService,
            AuthRateLimiter authRateLimiter
    ) {
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.authRateLimiter = authRateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        authRateLimiter.checkRegister(clientKey(httpRequest, request.email()));
        AuthResponse body = authService.register(request);
        addRefreshCookie(response, body.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        authRateLimiter.checkLogin(clientKey(httpRequest, request.email()));
        AuthResponse body = authService.login(request);
        addRefreshCookie(response, body.refreshToken());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = AuthCookieService.REFRESH_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest body,
            HttpServletResponse response
    ) {
        String rawToken = resolveRefreshToken(body, cookieToken);
        AuthResponse tokens = authService.refresh(rawToken);
        addRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = AuthCookieService.REFRESH_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest body,
            HttpServletResponse response
    ) {
        authService.logout(resolveRefreshToken(body, cookieToken));
        addCookie(response, authCookieService.clearRefreshCookie());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        authRateLimiter.checkForgotPassword(clientKey(httpRequest, request.email()));
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(request);
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me();
    }

    private String resolveRefreshToken(RefreshRequest body, String cookieToken) {
        if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
            return body.refreshToken();
        }
        return cookieToken;
    }

    private static String clientKey(HttpServletRequest request, String email) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return ip + "|" + (email == null ? "" : email);
    }

    private void addRefreshCookie(HttpServletResponse response, String rawToken) {
        addCookie(response, authCookieService.createRefreshCookie(rawToken));
    }

    private void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
