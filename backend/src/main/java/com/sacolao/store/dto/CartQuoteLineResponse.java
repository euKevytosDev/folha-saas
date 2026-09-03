package com.sacolao.store.dto;

import com.sacolao.product.entity.ProductUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record CartQuoteLineResponse(
        UUID productId,
        String name,
        String imageUrl,
        ProductUnit unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        String issue
) {
}
