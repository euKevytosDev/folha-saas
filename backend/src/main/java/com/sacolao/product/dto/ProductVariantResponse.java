package com.sacolao.product.dto;

import com.sacolao.product.entity.VariantPriceMode;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        String name,
        BigDecimal price,
        VariantPriceMode priceMode,
        boolean available,
        int sortOrder
) {
}
