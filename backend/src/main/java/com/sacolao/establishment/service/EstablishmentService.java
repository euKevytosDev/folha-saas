package com.sacolao.establishment.service;

import com.sacolao.common.exception.ForbiddenException;
import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.util.Slugify;
import com.sacolao.establishment.dto.CreateEstablishmentRequest;
import com.sacolao.establishment.dto.EstablishmentResponse;
import com.sacolao.establishment.dto.UpdateEstablishmentRequest;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.entity.PlanCode;
import com.sacolao.establishment.entity.StoreOpenMode;
import com.sacolao.establishment.mapper.EstablishmentMapper;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.security.AuthenticatedUser;
import com.sacolao.security.SecurityUtils;
import com.sacolao.tenant.TenantContext;
import com.sacolao.user.entity.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EstablishmentService {

    private final EstablishmentRepository establishmentRepository;
    private final StoreAvailabilityService availabilityService;

    public EstablishmentService(
            EstablishmentRepository establishmentRepository,
            StoreAvailabilityService availabilityService
    ) {
        this.establishmentRepository = establishmentRepository;
        this.availabilityService = availabilityService;
    }

    @Transactional
    public Establishment createForSignup(String name) {
        Establishment establishment = new Establishment();
        establishment.setName(name.trim());
        establishment.setSlug(uniqueSlug(name));
        establishment.setPlanCode(PlanCode.BASIC);
        establishment.setActive(true);
        establishment.setStoreOpenMode(StoreOpenMode.AUTO);
        establishment.setTimezone("America/Sao_Paulo");
        establishment.setOpeningHours(availabilityService.serializeHours(StoreAvailabilityService.defaultHours()));
        return establishmentRepository.save(establishment);
    }

    @Transactional
    public EstablishmentResponse createBySuperAdmin(CreateEstablishmentRequest request) {
        requireSuperAdmin();
        return toResponse(createForSignup(request.name()));
    }

    @Transactional(readOnly = true)
    public EstablishmentResponse getById(UUID id) {
        AuthenticatedUser current = SecurityUtils.requireUser();
        if (current.role() == UserRole.SUPER_ADMIN) {
            return toResponse(findOrNotFound(id));
        }
        UUID tenantId = TenantContext.get().orElseThrow(this::notFound);
        if (!tenantId.equals(id)) {
            throw notFound();
        }
        return toResponse(findOrNotFound(id));
    }

    @Transactional(readOnly = true)
    public EstablishmentResponse getCurrent() {
        UUID tenantId = TenantContext.require();
        return toResponse(findOrNotFound(tenantId));
    }

    @Transactional(readOnly = true)
    public List<EstablishmentResponse> listAllForSuperAdmin() {
        requireSuperAdmin();
        return establishmentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EstablishmentResponse update(UUID id, UpdateEstablishmentRequest request) {
        AuthenticatedUser current = SecurityUtils.requireUser();
        if (current.role() == UserRole.SUPER_ADMIN) {
            throw new ForbiddenException("Ação operacional do tenant não permitida para SUPER_ADMIN");
        }
        if (current.role() == UserRole.STAFF) {
            throw new ForbiddenException("Acesso negado");
        }
        UUID tenantId = TenantContext.require();
        if (!tenantId.equals(id)) {
            throw notFound();
        }
        Establishment establishment = findOrNotFound(id);
        if (request.name() != null) {
            establishment.setName(request.name().trim());
        }
        if (request.logoUrl() != null) {
            establishment.setLogoUrl(blankToNull(request.logoUrl()));
        }
        if (request.coverUrl() != null) {
            establishment.setCoverUrl(blankToNull(request.coverUrl()));
        }
        if (request.description() != null) {
            establishment.setDescription(blankToNull(request.description()));
        }
        if (request.phone() != null) {
            establishment.setPhone(blankToNull(request.phone()));
        }
        if (request.email() != null) {
            establishment.setEmail(blankToNull(request.email()));
        }
        if (request.address() != null) {
            establishment.setAddress(blankToNull(request.address()));
        }
        if (request.city() != null) {
            establishment.setCity(blankToNull(request.city()));
        }
        if (request.state() != null) {
            establishment.setState(blankToNull(request.state()));
        }
        if (request.zipCode() != null) {
            establishment.setZipCode(blankToNull(request.zipCode()));
        }
        if (request.storeOpenMode() != null) {
            establishment.setStoreOpenMode(request.storeOpenMode());
        }
        if (request.timezone() != null) {
            establishment.setTimezone(request.timezone());
        }
        if (request.openingHours() != null) {
            establishment.setOpeningHours(availabilityService.serializeHours(request.openingHours()));
        }
        return toResponse(establishment);
    }

    @Transactional
    public EstablishmentResponse changeStatus(UUID id, boolean active) {
        AuthenticatedUser current = SecurityUtils.requireUser();
        if (current.role() == UserRole.SUPER_ADMIN) {
            Establishment establishment = findOrNotFound(id);
            establishment.setActive(active);
            return toResponse(establishment);
        }
        if (current.role() != UserRole.OWNER) {
            throw new ForbiddenException("Acesso negado");
        }
        UUID tenantId = TenantContext.require();
        if (!tenantId.equals(id)) {
            throw notFound();
        }
        Establishment establishment = findOrNotFound(id);
        establishment.setActive(active);
        return toResponse(establishment);
    }

    private EstablishmentResponse toResponse(Establishment establishment) {
        return EstablishmentMapper.toResponse(establishment, availabilityService);
    }

    private Establishment findOrNotFound(UUID id) {
        return establishmentRepository.findById(id).orElseThrow(this::notFound);
    }

    private String uniqueSlug(String name) {
        String base = Slugify.from(name);
        String slug = base;
        int suffix = 2;
        while (establishmentRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix;
            suffix++;
        }
        return slug;
    }

    private void requireSuperAdmin() {
        AuthenticatedUser current = SecurityUtils.requireUser();
        if (current.role() != UserRole.SUPER_ADMIN) {
            throw new ForbiddenException("Acesso negado");
        }
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Recurso não encontrado");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
