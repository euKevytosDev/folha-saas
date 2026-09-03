package com.sacolao.store.controller;

import com.sacolao.product.dto.ProductResponse;
import com.sacolao.store.dto.CartQuoteRequest;
import com.sacolao.store.dto.CartQuoteResponse;
import com.sacolao.store.dto.PublicCatalogResponse;
import com.sacolao.store.dto.PublicStoreResponse;
import com.sacolao.store.service.StoreCatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/store/{slug}")
public class StoreCatalogController {

    private final StoreCatalogService storeCatalogService;

    public StoreCatalogController(StoreCatalogService storeCatalogService) {
        this.storeCatalogService = storeCatalogService;
    }

    @GetMapping
    public PublicStoreResponse getStore(@PathVariable String slug) {
        return storeCatalogService.getStore(slug);
    }

    @GetMapping("/catalog")
    public PublicCatalogResponse catalog(
            @PathVariable String slug,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String q
    ) {
        return storeCatalogService.getCatalog(slug, categoryId, q);
    }

    @GetMapping("/products/{productId}")
    public ProductResponse product(@PathVariable String slug, @PathVariable UUID productId) {
        return storeCatalogService.getProduct(slug, productId);
    }

    @PostMapping("/cart/quote")
    public CartQuoteResponse quote(@PathVariable String slug, @Valid @RequestBody CartQuoteRequest request) {
        return storeCatalogService.quote(slug, request);
    }
}
