package com.sacolao.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        String name,
        BigDecimal price,
        boolean available,
        int sortOrder
) {
}
