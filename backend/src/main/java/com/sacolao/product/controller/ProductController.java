package com.sacolao.product.controller;

import com.sacolao.product.dto.CreateProductRequest;
import com.sacolao.product.dto.ProductFlagRequest;
import com.sacolao.product.dto.ProductResponse;
import com.sacolao.product.dto.UpdateProductRequest;
import com.sacolao.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STAFF')")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productService.list();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return productService.get(id);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/availability")
    public ProductResponse availability(@PathVariable UUID id, @Valid @RequestBody ProductFlagRequest request) {
        return productService.setAvailable(id, request.value());
    }

    @PatchMapping("/{id}/featured")
    public ProductResponse featured(@PathVariable UUID id, @Valid @RequestBody ProductFlagRequest request) {
        return productService.setFeatured(id, request.value());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        productService.delete(id);
    }
}
