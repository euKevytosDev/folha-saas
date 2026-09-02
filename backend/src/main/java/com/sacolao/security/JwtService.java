package com.sacolao.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sacolao.config.AppProperties;
import com.sacolao.user.entity.User;
import com.sacolao.user.entity.UserRole;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final byte[] secret;
    private final Duration accessTtl;

    public JwtService(AppProperties properties) {
        String configured = properties.jwt().secret();
        if (configured == null || configured.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET deve ter pelo menos 32 bytes");
        }
        this.secret = configured.getBytes(StandardCharsets.UTF_8);
        this.accessTtl = Duration.ofMillis(properties.jwt().accessTokenExpirationMs());
    }

    public String issueAccessToken(User user) {
        return issueAccessToken(user, accessTtl);
    }

    public String issueAccessToken(User user, Duration ttl) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(user.getId().toString())
                    .claim("role", user.getRole().name())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(ttl)))
                    .jwtID(UUID.randomUUID().toString());
            if (user.getEstablishmentId() != null) {
                claims.claim("establishmentId", user.getEstablishmentId().toString());
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Falha ao emitir token", ex);
        }
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public JwtPayload parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new JwtException("Algoritmo inválido");
            }
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new JwtException("Assinatura inválida");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
                throw new JwtException("Token expirado");
            }
            UUID userId = UUID.fromString(claims.getSubject());
            UserRole role = UserRole.valueOf(claims.getStringClaim("role"));
            String establishment = claims.getStringClaim("establishmentId");
            UUID establishmentId = establishment == null || establishment.isBlank() ? null : UUID.fromString(establishment);
            return new JwtPayload(userId, role, establishmentId);
        } catch (ParseException | JOSEException | IllegalArgumentException ex) {
            throw new JwtException("Token inválido");
        }
    }

    public static class JwtException extends RuntimeException {
        public JwtException(String message) {
            super(message);
        }
    }

    public record JwtPayload(UUID userId, UserRole role, UUID establishmentId) {
    }
}
