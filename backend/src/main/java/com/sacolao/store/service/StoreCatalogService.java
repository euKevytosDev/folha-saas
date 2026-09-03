package com.sacolao.store.service;

import com.sacolao.category.dto.CategoryResponse;
import com.sacolao.category.mapper.CategoryMapper;
import com.sacolao.category.repository.CategoryRepository;
import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.product.dto.ProductResponse;
import com.sacolao.product.mapper.ProductMapper;
import com.sacolao.product.repository.ProductRepository;
import com.sacolao.common.util.Money;
import com.sacolao.product.entity.Product;
import com.sacolao.store.dto.CartQuoteItemRequest;
import com.sacolao.store.dto.CartQuoteLineResponse;
import com.sacolao.store.dto.CartQuoteRequest;
import com.sacolao.store.dto.CartQuoteResponse;
import com.sacolao.store.dto.PublicCatalogResponse;
import com.sacolao.store.dto.PublicStoreResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class StoreCatalogService {

    private final EstablishmentRepository establishmentRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public StoreCatalogService(
            EstablishmentRepository establishmentRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository
    ) {
        this.establishmentRepository = establishmentRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public PublicStoreResponse getStore(String slug) {
        return toPublicStore(requireActiveStore(slug));
    }

    @Transactional(readOnly = true)
    public PublicCatalogResponse getCatalog(String slug, UUID categoryId, String query) {
        Establishment store = requireActiveStore(slug);
        String normalizedQuery = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        List<CategoryResponse> categories = categoryRepository
                .findByEstablishment_IdAndActiveTrueOrderBySortOrderAscNameAsc(store.getId())
                .stream()
                .map(CategoryMapper::toResponse)
                .toList();
        List<ProductResponse> products = productRepository
                .searchPublic(store.getId(), categoryId, normalizedQuery, normalizedQuery != null)
                .stream()
                .map(ProductMapper::toResponse)
                .toList();
        List<ProductResponse> featured = products.stream()
                .filter(ProductResponse::featured)
                .toList();
        return new PublicCatalogResponse(toPublicStore(store), categories, featured, products);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(String slug, UUID productId) {
        Establishment store = requireActiveStore(slug);
        return productRepository.findPublicById(productId, store.getId())
                .map(ProductMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }

    @Transactional(readOnly = true)
    public CartQuoteResponse quote(String slug, CartQuoteRequest request) {
        Establishment store = requireActiveStore(slug);
        List<CartQuoteLineResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CartQuoteItemRequest item : request.items()) {
            Product product = productRepository.findPublicById(item.productId(), store.getId()).orElse(null);
            BigDecimal quantity = Money.quantity(item.quantity());
            if (product == null) {
                lines.add(unavailableLine(item.productId(), quantity, "Produto indisponível"));
                continue;
            }
            String issue = validateQuantity(product, quantity);
            BigDecimal unitPrice = Money.of(product.getPrice());
            BigDecimal lineTotal = issue == null
                    ? unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (issue == null) {
                subtotal = subtotal.add(lineTotal);
            }
            lines.add(new CartQuoteLineResponse(
                    product.getId(),
                    product.getName(),
                    product.getImageUrl(),
                    product.getUnit(),
                    quantity,
                    unitPrice,
                    lineTotal,
                    issue
            ));
        }
        BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deliveryFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new CartQuoteResponse(lines, subtotal, discount, deliveryFee, subtotal.add(deliveryFee).subtract(discount));
    }

    private String validateQuantity(Product product, BigDecimal quantity) {
        if (quantity.compareTo(product.getMinimumQuantity()) < 0) {
            return "Quantidade mínima não atingida";
        }
        if (!product.getUnit().decimalAllowed() && quantity.stripTrailingZeros().scale() > 0) {
            return "Esta unidade não aceita quantidade decimal";
        }
        if (product.isStockControlled()
                && product.getStockQuantity() != null
                && quantity.compareTo(product.getStockQuantity()) > 0) {
            return "Quantidade acima do estoque";
        }
        return null;
    }

    private CartQuoteLineResponse unavailableLine(UUID productId, BigDecimal quantity, String issue) {
        return new CartQuoteLineResponse(productId, null, null, null, quantity, null, BigDecimal.ZERO.setScale(2), issue);
    }

    private Establishment requireActiveStore(String slug) {
        return establishmentRepository.findBySlug(slug)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Loja não encontrada"));
    }

    private PublicStoreResponse toPublicStore(Establishment establishment) {
        return new PublicStoreResponse(
                establishment.getId(),
                establishment.getName(),
                establishment.getSlug(),
                establishment.getLogoUrl(),
                establishment.getDescription(),
                establishment.getPhone(),
                establishment.getAddress(),
                establishment.getCity(),
                establishment.getState(),
                establishment.isActive()
        );
    }
}
