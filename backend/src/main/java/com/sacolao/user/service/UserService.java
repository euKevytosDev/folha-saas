package com.sacolao.user.service;

import com.sacolao.common.exception.ConflictException;
import com.sacolao.common.exception.ForbiddenException;
import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.EmailNormalizer;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.security.AuthenticatedUser;
import com.sacolao.security.SecurityUtils;
import com.sacolao.tenant.TenantContext;
import com.sacolao.user.dto.CreateUserRequest;
import com.sacolao.user.dto.UpdateUserRequest;
import com.sacolao.user.dto.UserResponse;
import com.sacolao.user.entity.User;
import com.sacolao.user.entity.UserRole;
import com.sacolao.user.mapper.UserMapper;
import com.sacolao.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EstablishmentRepository establishmentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            EstablishmentRepository establishmentRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.establishmentRepository = establishmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createOwner(Establishment establishment, String name, String email, String rawPassword) {
        User user = new User();
        user.setEstablishment(establishment);
        user.setName(name.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.OWNER);
        user.setActive(true);
        return userRepository.save(user);
    }

    @Transactional
    public User createSuperAdmin(String name, String email, String rawPassword) {
        User user = new User();
        user.setEstablishment(null);
        user.setName(name.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.SUPER_ADMIN);
        user.setActive(true);
        return userRepository.save(user);
    }

    @Transactional
    public UserResponse createMember(CreateUserRequest request) {
        AuthenticatedUser current = SecurityUtils.requireUser();
        UUID tenantId = TenantContext.require();
        UserRole requested = request.role();
        if (requested == UserRole.SUPER_ADMIN || requested == UserRole.OWNER) {
            throw new UnprocessableException("INVALID_ROLE", "Não é permitido criar este tipo de usuário");
        }
        if (current.role() == UserRole.ADMIN && requested != UserRole.STAFF) {
            throw new ForbiddenException("Acesso negado");
        }
        if (current.role() != UserRole.OWNER && current.role() != UserRole.ADMIN) {
            throw new ForbiddenException("Acesso negado");
        }
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "Este e-mail já está em uso");
        }
        Establishment establishment = establishmentRepository.getReferenceById(tenantId);
        User user = new User();
        user.setEstablishment(establishment);
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(requested);
        user.setActive(true);
        return UserMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listCurrentTenant() {
        UUID tenantId = TenantContext.require();
        return userRepository.findByEstablishment_IdOrderByCreatedAtAsc(tenantId).stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getInCurrentTenant(UUID id) {
        UUID tenantId = TenantContext.require();
        User user = userRepository.findByIdAndEstablishment_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateInCurrentTenant(UUID id, UpdateUserRequest request) {
        AuthenticatedUser current = SecurityUtils.requireUser();
        UUID tenantId = TenantContext.require();
        User user = userRepository.findByIdAndEstablishment_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
        if (user.getRole() == UserRole.OWNER && current.role() != UserRole.OWNER) {
            throw new ForbiddenException("Acesso negado");
        }
        if (request.role() != null) {
            if (request.role() == UserRole.SUPER_ADMIN || request.role() == UserRole.OWNER) {
                throw new UnprocessableException("INVALID_ROLE", "Não é permitido atribuir este tipo de usuário");
            }
            if (user.getRole() == UserRole.OWNER) {
                throw new UnprocessableException("INVALID_ROLE", "Não é permitido alterar o perfil do proprietário");
            }
            if (current.role() == UserRole.ADMIN && request.role() != UserRole.STAFF) {
                throw new ForbiddenException("Acesso negado");
            }
            user.setRole(request.role());
        }
        if (request.name() != null) {
            user.setName(request.name().trim());
        }
        if (request.active() != null) {
            if (user.getId().equals(current.id())) {
                throw new UnprocessableException("INVALID_STATUS", "Você não pode desativar a própria conta");
            }
            user.setActive(request.active());
        }
        return UserMapper.toResponse(user);
    }
}
