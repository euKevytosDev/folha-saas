package com.sacolao.payment.controller;

import com.sacolao.payment.dto.PaymentResponse;
import com.sacolao.payment.dto.PaymentSettingsResponse;
import com.sacolao.payment.dto.UpdatePaymentSettingsRequest;
import com.sacolao.payment.service.PaymentService;
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
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payments/settings")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public PaymentSettingsResponse getSettings() {
        return paymentService.getSettings();
    }

    @PutMapping("/payments/settings")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public PaymentSettingsResponse updateSettings(@Valid @RequestBody UpdatePaymentSettingsRequest request) {
        return paymentService.updateSettings(request);
    }

    @GetMapping("/orders/{orderId}/payment")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public PaymentResponse getByOrder(@PathVariable UUID orderId) {
        return paymentService.getByOrder(orderId);
    }

    @PostMapping("/orders/{orderId}/payment/confirm")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
    public PaymentResponse confirmManual(@PathVariable UUID orderId) {
        return paymentService.confirmManual(orderId);
    }
}
