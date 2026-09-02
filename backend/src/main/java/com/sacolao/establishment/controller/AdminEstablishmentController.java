package com.sacolao.establishment.controller;

import com.sacolao.establishment.dto.EstablishmentResponse;
import com.sacolao.establishment.service.EstablishmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/establishments")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminEstablishmentController {

    private final EstablishmentService establishmentService;

    public AdminEstablishmentController(EstablishmentService establishmentService) {
        this.establishmentService = establishmentService;
    }

    @GetMapping
    public List<EstablishmentResponse> list() {
        return establishmentService.listAllForSuperAdmin();
    }
}
