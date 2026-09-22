package com.sacolao.product.mapper;

import com.sacolao.product.dto.ProductResponse;
import com.sacolao.product.entity.Product;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getEstablishmentId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getPrice(),
                product.getCompareAtPrice(),
                product.getUnit(),
                product.isAvailable(),
                product.isFeatured(),
                product.isStockControlled(),
                product.getStockQuantity(),
                product.getMinimumQuantity(),
                product.getNcm(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
