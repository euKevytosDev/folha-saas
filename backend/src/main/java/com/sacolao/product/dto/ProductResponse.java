package com.sacolao.product.dto;

import com.sacolao.product.entity.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID establishmentId,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        BigDecimal compareAtPrice,
        ProductUnit unit,
        boolean available,
        boolean featured,
        boolean stockControlled,
        BigDecimal stockQuantity,
        BigDecimal minimumQuantity,
        Instant createdAt,
        Instant updatedAt
) {
}
