package com.sacolao.establishment.controller;

import com.sacolao.establishment.dto.CreateEstablishmentRequest;
import com.sacolao.establishment.dto.EstablishmentResponse;
import com.sacolao.establishment.dto.EstablishmentStatusRequest;
import com.sacolao.establishment.dto.UpdateEstablishmentRequest;
import com.sacolao.establishment.service.EstablishmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/establishments")
public class EstablishmentController {

    private final EstablishmentService establishmentService;

    public EstablishmentController(EstablishmentService establishmentService) {
        this.establishmentService = establishmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public EstablishmentResponse create(@Valid @RequestBody CreateEstablishmentRequest request) {
        return establishmentService.createBySuperAdmin(request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public EstablishmentResponse me() {
        return establishmentService.getCurrent();
    }

    @GetMapping("/{id}")
    public EstablishmentResponse get(@PathVariable UUID id) {
        return establishmentService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public EstablishmentResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEstablishmentRequest request
    ) {
        return establishmentService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN')")
    public EstablishmentResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody EstablishmentStatusRequest request
    ) {
        return establishmentService.changeStatus(id, request.active());
    }
}
