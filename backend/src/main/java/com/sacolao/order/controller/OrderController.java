package com.sacolao.order.controller;

import com.sacolao.order.dto.CashReportResponse;
import com.sacolao.order.dto.OrderResponse;
import com.sacolao.order.dto.OrderSummaryResponse;
import com.sacolao.order.dto.UpdateOrderStatusRequest;
import com.sacolao.order.entity.OrderStatus;
import com.sacolao.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/summary")
    public OrderSummaryResponse summary() {
        return orderService.summary();
    }

    @GetMapping("/cash-report")
    public CashReportResponse cashReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return orderService.cashReport(from, to);
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) {
        return orderService.list(status);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id);
    }

    @DeleteMapping("/{id}/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideFromHistory(@PathVariable UUID id) {
        orderService.hideFromHistory(id);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateStatus(id, request);
    }
}
