package com.sacolao.category.mapper;

import com.sacolao.category.dto.CategoryResponse;
import com.sacolao.category.entity.Category;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getEstablishmentId(),
                category.getName(),
                category.getDescription(),
                category.getImageUrl(),
                category.getSortOrder(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
