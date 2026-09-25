package com.sacolao.product.dto;

import com.sacolao.product.entity.ProductUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        @Size(max = 500) String imageUrl,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal price,
        @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal compareAtPrice,
        @NotNull ProductUnit unit,
        Boolean available,
        Boolean featured,
        Boolean stockControlled,
        @DecimalMin(value = "0.000") @Digits(integer = 9, fraction = 3) BigDecimal stockQuantity,
        @DecimalMin(value = "0.001") @Digits(integer = 9, fraction = 3) BigDecimal minimumQuantity,
        @Size(max = 8) String ncm,
        List<@Valid ProductVariantRequest> variants
) {
}
