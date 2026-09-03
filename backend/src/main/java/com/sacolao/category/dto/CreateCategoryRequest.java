package com.sacolao.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Size(max = 500) String imageUrl,
        Integer sortOrder,
        Boolean active
) {
}
