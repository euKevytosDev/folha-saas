package com.sacolao.order.controller;

import com.sacolao.order.dto.CheckoutRequest;
import com.sacolao.order.dto.OrderResponse;
import com.sacolao.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store/{slug}/orders")
public class StoreOrderController {

    private final OrderService orderService;

    public StoreOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            @PathVariable String slug,
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return orderService.checkout(slug, request, idempotencyKey);
    }

    @GetMapping("/{publicCode}")
    public OrderResponse get(@PathVariable String slug, @PathVariable String publicCode) {
        return orderService.getPublic(slug, publicCode);
    }
}
