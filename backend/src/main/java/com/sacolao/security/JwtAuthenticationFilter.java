package com.sacolao.security;

import com.sacolao.tenant.TenantContext;
import com.sacolao.user.entity.User;
import com.sacolao.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthErrorWriter errorWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            AuthErrorWriter errorWriter
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                boolean accepted = authenticate(request, response, header.substring(7).trim());
                if (!accepted) {
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean authenticate(HttpServletRequest request, HttpServletResponse response, String token)
            throws IOException {
        JwtService.JwtPayload payload;
        try {
            payload = jwtService.parse(token);
        } catch (JwtService.JwtException ex) {
            errorWriter.write(request, response, 401, "UNAUTHORIZED", "Não autenticado");
            return false;
        }

        User user = userRepository.findWithEstablishmentById(payload.userId()).orElse(null);
        if (user == null || !user.isActive()) {
            errorWriter.write(request, response, 401, "UNAUTHORIZED", "Não autenticado");
            return false;
        }
        if (user.getRole() != payload.role() || !Objects.equals(user.getEstablishmentId(), payload.establishmentId())) {
            errorWriter.write(request, response, 401, "UNAUTHORIZED", "Não autenticado");
            return false;
        }
        if (user.getRole().isTenantBound()) {
            if (user.getEstablishment() == null || !user.getEstablishment().isActive()) {
                errorWriter.write(request, response, 401, "UNAUTHORIZED", "Não autenticado");
                return false;
            }
            TenantContext.set(user.getEstablishmentId());
        }

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getEstablishmentId()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return true;
    }
}
