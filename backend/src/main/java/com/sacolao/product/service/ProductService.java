package com.sacolao.product.service;

import com.sacolao.category.entity.Category;
import com.sacolao.category.service.CategoryService;
import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.Money;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.product.dto.CreateProductRequest;
import com.sacolao.product.dto.ProductResponse;
import com.sacolao.product.dto.ProductVariantRequest;
import com.sacolao.product.dto.UpdateProductRequest;
import com.sacolao.product.entity.Product;
import com.sacolao.product.entity.ProductUnit;
import com.sacolao.product.entity.ProductVariant;
import com.sacolao.product.mapper.ProductMapper;
import com.sacolao.product.repository.ProductRepository;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final EstablishmentRepository establishmentRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryService categoryService,
            EstablishmentRepository establishmentRepository
    ) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.establishmentRepository = establishmentRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        UUID tenantId = TenantContext.require();
        Establishment establishment = establishmentRepository.findById(tenantId)
                .orElseThrow(this::notFound);
        Category category = categoryService.requireInTenant(request.categoryId());
        Product product = new Product();
        product.setEstablishment(establishment);
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setDescription(blankToNull(request.description()));
        product.setImageUrl(blankToNull(request.imageUrl()));
        product.setPrice(requirePrice(request.price()));
        product.setCompareAtPrice(normalizeCompare(request.compareAtPrice(), product.getPrice()));
        product.setUnit(request.unit());
        product.setAvailable(request.available() == null || request.available());
        product.setFeatured(false);
        boolean controlled = request.stockQuantity() != null;
        product.setStockControlled(controlled);
        product.setStockQuantity(controlled ? Money.quantity(request.stockQuantity()) : null);
        product.setMinimumQuantity(normalizeMinimum(request.minimumQuantity(), request.unit()));
        product.setNcm(normalizeNcm(request.ncm()));
        validateStock(product);
        syncPromotionFeatured(product);
        syncVariants(product, request.variants());
        return ProductMapper.toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return productRepository.findAllInTenant(TenantContext.require()).stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductMapper.toResponse(requireInTenant(id));
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = requireInTenant(id);
        if (request.categoryId() != null) {
            product.setCategory(categoryService.requireInTenant(request.categoryId()));
        }
        if (request.name() != null && !request.name().isBlank()) {
            product.setName(request.name().trim());
        }
        if (request.description() != null) {
            product.setDescription(blankToNull(request.description()));
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(blankToNull(request.imageUrl()));
        }
        if (request.price() != null) {
            product.setPrice(requirePrice(request.price()));
        }
        if (request.compareAtPrice() != null) {
            product.setCompareAtPrice(normalizeCompare(request.compareAtPrice(), product.getPrice()));
        }
        if (request.unit() != null) {
            product.setUnit(request.unit());
        }
        if (request.available() != null) {
            product.setAvailable(request.available());
        }
        if (request.featured() != null) {
            product.setFeatured(request.featured());
        }
        if (request.stockControlled() != null) {
            product.setStockControlled(request.stockControlled());
        }
        if (request.stockQuantity() != null) {
            product.setStockQuantity(Money.quantity(request.stockQuantity()));
        }
        if (request.minimumQuantity() != null) {
            product.setMinimumQuantity(normalizeMinimum(request.minimumQuantity(), product.getUnit()));
        } else {
            product.setMinimumQuantity(normalizeMinimum(product.getMinimumQuantity(), product.getUnit()));
        }
        if (request.ncm() != null) {
            product.setNcm(normalizeNcm(request.ncm()));
        }
        validateStock(product);
        product.setCompareAtPrice(normalizeCompare(product.getCompareAtPrice(), product.getPrice()));
        syncPromotionFeatured(product);
        if (request.variants() != null) {
            syncVariants(product, request.variants());
        }
        return ProductMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse setAvailable(UUID id, boolean available) {
        Product product = requireInTenant(id);
        product.setAvailable(available);
        return ProductMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse setFeatured(UUID id, boolean featured) {
        Product product = requireInTenant(id);
        if (!featured) {
            product.setCompareAtPrice(null);
            product.setFeatured(false);
        } else if (product.getCompareAtPrice() != null) {
            product.setFeatured(true);
        } else {
            throw new UnprocessableException(
                    "PROMO_REQUIRES_COMPARE_PRICE",
                    "Para colocar em promoção, informe o preço antigo e o preço novo no cadastro do produto"
            );
        }
        return ProductMapper.toResponse(product);
    }

    /** Produtos com preço antigo entram na seção Promoção da vitrine. */
    private void syncPromotionFeatured(Product product) {
        product.setFeatured(product.getCompareAtPrice() != null);
    }

    private void syncVariants(Product product, List<ProductVariantRequest> requests) {
        product.getVariants().clear();
        if (requests == null || requests.isEmpty()) {
            return;
        }
        int index = 0;
        for (ProductVariantRequest request : requests) {
            if (request == null || request.name() == null || request.name().isBlank()) {
                continue;
            }
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setName(request.name().trim());
            variant.setPrice(requirePrice(request.price()));
            variant.setAvailable(request.available() == null || request.available());
            variant.setSortOrder(request.sortOrder() != null ? request.sortOrder() : index);
            product.getVariants().add(variant);
            index++;
        }
    }

    @Transactional
    public void delete(UUID id) {
        Product product = requireInTenant(id);
        productRepository.delete(product);
    }

    public Product requireInTenant(UUID id) {
        return productRepository.findByIdAndEstablishment_Id(id, TenantContext.require())
                .orElseThrow(this::notFound);
    }

    private BigDecimal requirePrice(BigDecimal price) {
        BigDecimal normalized = Money.of(price);
        if (normalized == null || normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnprocessableException("INVALID_PRICE", "O preço deve ser maior que zero");
        }
        return normalized;
    }

    private BigDecimal normalizeCompare(BigDecimal compareAtPrice, BigDecimal price) {
        if (compareAtPrice == null) {
            return null;
        }
        BigDecimal normalized = Money.of(compareAtPrice);
        if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (normalized.compareTo(price) <= 0) {
            throw new UnprocessableException("INVALID_COMPARE_PRICE", "O preço anterior deve ser maior que o preço atual");
        }
        return normalized;
    }

    private BigDecimal normalizeMinimum(BigDecimal minimum, ProductUnit unit) {
        BigDecimal value = Money.quantity(minimum == null ? BigDecimal.ONE : minimum);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UnprocessableException("INVALID_QUANTITY", "A quantidade mínima deve ser maior que zero");
        }
        if (unit != null && !unit.decimalAllowed() && value.stripTrailingZeros().scale() > 0) {
            throw new UnprocessableException("INVALID_QUANTITY", "Esta unidade não aceita quantidade decimal");
        }
        return value;
    }

    private void validateStock(Product product) {
        if (product.isStockControlled() && product.getStockQuantity() == null) {
            throw new UnprocessableException("STOCK_REQUIRED", "Informe a quantidade em estoque");
        }
    }

    private static String normalizeNcm(String ncm) {
        if (ncm == null || ncm.isBlank()) {
            return null;
        }
        String digits = ncm.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Recurso não encontrado");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
