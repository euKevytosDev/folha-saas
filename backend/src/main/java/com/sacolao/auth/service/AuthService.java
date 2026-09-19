package com.sacolao.auth.service;

import com.sacolao.auth.dto.AuthResponse;
import com.sacolao.auth.dto.ChangePasswordRequest;
import com.sacolao.auth.dto.ForgotPasswordRequest;
import com.sacolao.auth.dto.LoginRequest;
import com.sacolao.auth.dto.MeResponse;
import com.sacolao.auth.dto.MessageResponse;
import com.sacolao.auth.dto.RegisterRequest;
import com.sacolao.auth.dto.ResetPasswordRequest;
import com.sacolao.auth.token.PasswordResetToken;
import com.sacolao.auth.token.PasswordResetTokenRepository;
import com.sacolao.auth.token.RefreshToken;
import com.sacolao.auth.token.RefreshTokenRepository;
import com.sacolao.auth.token.SecureTokenFactory;
import com.sacolao.auth.token.TokenHash;
import com.sacolao.common.exception.ConflictException;
import com.sacolao.common.exception.UnauthorizedException;
import com.sacolao.common.util.EmailNormalizer;
import com.sacolao.config.AppProperties;
import com.sacolao.establishment.dto.EstablishmentResponse;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.mapper.EstablishmentMapper;
import com.sacolao.establishment.service.EstablishmentService;
import com.sacolao.establishment.service.StoreAvailabilityService;
import com.sacolao.security.AuthenticatedUser;
import com.sacolao.security.JwtService;
import com.sacolao.security.SecurityUtils;
import com.sacolao.user.entity.User;
import com.sacolao.user.mapper.UserMapper;
import com.sacolao.user.repository.UserRepository;
import com.sacolao.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String GENERIC_CREDENTIALS = "Credenciais inválidas";
    private static final String GENERIC_RESET = "Se o e-mail estiver cadastrado, enviaremos instruções para recuperação.";

    private final UserRepository userRepository;
    private final UserService userService;
    private final EstablishmentService establishmentService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecureTokenFactory tokenFactory;
    private final AppProperties properties;
    private final Environment environment;
    private final StoreAvailabilityService availabilityService;

    public AuthService(
            UserRepository userRepository,
            UserService userService,
            EstablishmentService establishmentService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            SecureTokenFactory tokenFactory,
            AppProperties properties,
            Environment environment,
            StoreAvailabilityService availabilityService
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.establishmentService = establishmentService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.tokenFactory = tokenFactory;
        this.properties = properties;
        this.environment = environment;
        this.availabilityService = availabilityService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Este e-mail já está em uso");
        }
        Establishment establishment = establishmentService.createForSignup(request.establishmentName());
        User owner = userService.createOwner(establishment, request.name(), email, request.password());
        log.info("Estabelecimento criado id={}", establishment.getId());
        return issueSession(owner);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        User user = userRepository.findWithEstablishmentByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(GENERIC_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new UnauthorizedException(GENERIC_CREDENTIALS);
        }
        if (user.getRole().isTenantBound() && (user.getEstablishment() == null || !user.getEstablishment().isActive())) {
            throw new UnauthorizedException(GENERIC_CREDENTIALS);
        }
        user.setLastLoginAt(Instant.now());
        return issueSession(user);
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Não autenticado");
        }
        RefreshToken stored = refreshTokenRepository.findWithUserByTokenHash(TokenHash.sha256(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Não autenticado"));
        Instant now = Instant.now();
        if (stored.isRevoked()) {
            refreshTokenRepository.revokeAllActive(stored.getUser().getId(), now);
            throw new UnauthorizedException("Não autenticado");
        }
        if (stored.isExpired(now)) {
            stored.setRevokedAt(now);
            throw new UnauthorizedException("Não autenticado");
        }
        User user = stored.getUser();
        if (!user.isActive()) {
            stored.setRevokedAt(now);
            throw new UnauthorizedException("Não autenticado");
        }
        stored.setRevokedAt(now);
        return issueSession(user);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHash.sha256(rawToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.setRevokedAt(Instant.now());
            }
        });
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        userRepository.findByEmail(email).ifPresent(user -> {
            Instant now = Instant.now();
            passwordResetTokenRepository.expireUnused(user.getId(), now);
            String rawToken = tokenFactory.generate();
            PasswordResetToken reset = new PasswordResetToken();
            reset.setUser(user);
            reset.setTokenHash(TokenHash.sha256(rawToken));
            reset.setExpiresAt(now.plusMillis(properties.auth().passwordResetExpirationMs()));
            passwordResetTokenRepository.save(reset);
            log.info("Recuperação de senha solicitada userId={}", user.getId());
            if (environment.matchesProfiles("dev")) {
                log.info("Link de recuperação (somente dev): /redefinir-senha?token={}", rawToken);
            }
        });
        return new MessageResponse(GENERIC_RESET);
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        Instant now = Instant.now();
        PasswordResetToken reset = passwordResetTokenRepository.findWithUserByTokenHash(TokenHash.sha256(request.token()))
                .orElseThrow(() -> new UnauthorizedException("Token inválido"));
        if (reset.isUsed() || reset.isExpired(now)) {
            throw new UnauthorizedException("Token inválido");
        }
        User user = reset.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        reset.setUsedAt(now);
        refreshTokenRepository.revokeAllActive(user.getId(), now);
        return new MessageResponse("Senha atualizada");
    }

    @Transactional
    public MessageResponse changePassword(ChangePasswordRequest request) {
        AuthenticatedUser current = SecurityUtils.requireUser();
        User user = userRepository.findById(current.id())
                .orElseThrow(() -> new UnauthorizedException("Não autenticado"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException(GENERIC_CREDENTIALS);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllActive(user.getId(), Instant.now());
        return new MessageResponse("Senha atualizada");
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AuthenticatedUser current = SecurityUtils.requireUser();
        User user = userRepository.findWithEstablishmentById(current.id())
                .orElseThrow(() -> new UnauthorizedException("Não autenticado"));
        EstablishmentResponse establishment = user.getEstablishment() == null
                ? null
                : EstablishmentMapper.toResponse(user.getEstablishment(), availabilityService);
        return new MeResponse(UserMapper.toResponse(user), establishment);
    }

    private AuthResponse issueSession(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        String rawRefresh = tokenFactory.generate();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(TokenHash.sha256(rawRefresh));
        refreshToken.setExpiresAt(Instant.now().plusMillis(properties.jwt().refreshTokenExpirationMs()));
        refreshTokenRepository.save(refreshToken);
        EstablishmentResponse establishment = user.getEstablishment() == null
                ? null
                : EstablishmentMapper.toResponse(user.getEstablishment(), availabilityService);
        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.accessTtl().toSeconds(),
                rawRefresh,
                UserMapper.toResponse(user),
                establishment
        );
    }
}
