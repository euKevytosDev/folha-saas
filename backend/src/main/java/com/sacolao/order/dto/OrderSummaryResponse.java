package com.sacolao.order.dto;

public record OrderSummaryResponse(
        long total,
        long pending,
        long confirmed,
        long preparing,
        long dispatched,
        long delivered,
        long cancelled
) {
}
