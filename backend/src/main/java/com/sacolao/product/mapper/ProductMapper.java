package com.sacolao.product.mapper;

import com.sacolao.product.dto.ProductResponse;
import com.sacolao.product.dto.ProductVariantResponse;
import com.sacolao.product.entity.Product;

import java.util.List;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product product) {
        List<ProductVariantResponse> variants = product.getVariants().stream()
                .map(variant -> new ProductVariantResponse(
                        variant.getId(),
                        variant.getName(),
                        variant.getPrice(),
                        variant.isAvailable(),
                        variant.getSortOrder()
                ))
                .toList();
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
                product.getMaximumQuantity(),
                product.getVariantMinChoices(),
                product.getVariantMaxChoices(),
                product.getNcm(),
                variants,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
