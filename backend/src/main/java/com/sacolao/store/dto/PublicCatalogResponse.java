package com.sacolao.store.dto;

import com.sacolao.category.dto.CategoryResponse;
import com.sacolao.product.dto.ProductResponse;

import java.util.List;

public record PublicCatalogResponse(
        PublicStoreResponse store,
        List<CategoryResponse> categories,
        List<ProductResponse> featured,
        List<ProductResponse> products
) {
}
