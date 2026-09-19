package com.sacolao.payment.controller;

import com.sacolao.payment.dto.PaymentResponse;
import com.sacolao.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store/{slug}/orders/{publicCode}/payment")
public class StorePaymentController {

    private final PaymentService paymentService;

    public StorePaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public PaymentResponse get(@PathVariable String slug, @PathVariable String publicCode) {
        return paymentService.getPublic(slug, publicCode);
    }

    @PostMapping("/simulate")
    public PaymentResponse simulate(@PathVariable String slug, @PathVariable String publicCode) {
        return paymentService.simulatePaid(slug, publicCode);
    }
}
