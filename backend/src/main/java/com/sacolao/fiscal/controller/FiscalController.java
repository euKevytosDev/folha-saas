package com.sacolao.fiscal.controller;

import com.sacolao.fiscal.dto.FiscalConnectionTestResponse;
import com.sacolao.fiscal.dto.FiscalSettingsResponse;
import com.sacolao.fiscal.dto.NfceInfoResponse;
import com.sacolao.fiscal.dto.UpdateFiscalSettingsRequest;
import com.sacolao.fiscal.service.FiscalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FiscalController {

    private final FiscalService fiscalService;

    public FiscalController(FiscalService fiscalService) {
        this.fiscalService = fiscalService;
    }

    @GetMapping("/fiscal/settings")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public FiscalSettingsResponse getSettings() {
        return fiscalService.getSettings();
    }

    @PutMapping("/fiscal/settings")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public FiscalSettingsResponse updateSettings(@Valid @RequestBody UpdateFiscalSettingsRequest request) {
        return fiscalService.updateSettings(request);
    }

    @PostMapping("/fiscal/settings/test")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public FiscalConnectionTestResponse testConnection() {
        return fiscalService.testConnection();
    }

    @PostMapping("/orders/{orderId}/nfce")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public NfceInfoResponse emit(@PathVariable UUID orderId) {
        return fiscalService.emitForOrder(orderId);
    }

    @PostMapping("/orders/{orderId}/nfce/refresh")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public NfceInfoResponse refresh(@PathVariable UUID orderId) {
        return fiscalService.consultForOrder(orderId);
    }
}
