package com.sacolao.delivery.controller;

import com.sacolao.delivery.dto.DeliverySettingsResponse;
import com.sacolao.delivery.dto.UpdateDeliverySettingsRequest;
import com.sacolao.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery/settings")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class DeliverySettingsController {

    private final DeliveryService deliveryService;

    public DeliverySettingsController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public DeliverySettingsResponse get() {
        return deliveryService.getSettings();
    }

    @PutMapping
    public DeliverySettingsResponse update(@Valid @RequestBody UpdateDeliverySettingsRequest request) {
        return deliveryService.updateSettings(request);
    }
}
