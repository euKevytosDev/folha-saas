package com.sacolao.store.service;

import com.sacolao.category.dto.CategoryResponse;
import com.sacolao.category.mapper.CategoryMapper;
import com.sacolao.category.repository.CategoryRepository;
import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.Money;
import com.sacolao.coupon.service.CouponService;
import com.sacolao.delivery.entity.EstablishmentDeliverySettings;
import com.sacolao.delivery.service.DeliveryService;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.establishment.service.StoreAvailabilityService;
import com.sacolao.order.entity.FulfillmentType;
import com.sacolao.product.dto.ProductResponse;
import com.sacolao.product.entity.Product;
import com.sacolao.product.entity.ProductVariant;
import com.sacolao.product.mapper.ProductMapper;
import com.sacolao.product.repository.ProductRepository;
import com.sacolao.payment.entity.EstablishmentPaymentSettings;
import com.sacolao.payment.repository.EstablishmentPaymentSettingsRepository;
import com.sacolao.store.dto.CartQuoteItemRequest;
import com.sacolao.store.dto.CartQuoteLineResponse;
import com.sacolao.store.dto.CartQuoteRequest;
import com.sacolao.store.dto.CartQuoteResponse;
import com.sacolao.store.dto.PublicCatalogResponse;
import com.sacolao.store.dto.PublicPaymentOptionsResponse;
import com.sacolao.store.dto.PublicStoreResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class StoreCatalogService {

    private final EstablishmentRepository establishmentRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final DeliveryService deliveryService;
    private final CouponService couponService;
    private final StoreAvailabilityService availabilityService;
    private final EstablishmentPaymentSettingsRepository paymentSettingsRepository;

    public StoreCatalogService(
            EstablishmentRepository establishmentRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            DeliveryService deliveryService,
            CouponService couponService,
            StoreAvailabilityService availabilityService,
            EstablishmentPaymentSettingsRepository paymentSettingsRepository
    ) {
        this.establishmentRepository = establishmentRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.deliveryService = deliveryService;
        this.couponService = couponService;
        this.availabilityService = availabilityService;
        this.paymentSettingsRepository = paymentSettingsRepository;
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
                .filter(product -> product.compareAtPrice() != null)
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

    @Transactional
    public CartQuoteResponse quote(String slug, CartQuoteRequest request) {
        Establishment store = requireActiveStore(slug);
        EstablishmentDeliverySettings deliverySettings = deliveryService.requireSettings(store.getId());
        FulfillmentType fulfillment = request.fulfillmentType() == null
                ? FulfillmentType.PICKUP
                : request.fulfillmentType();
        deliveryService.assertFulfillmentAllowed(deliverySettings, fulfillment);

        List<CartQuoteLineResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (CartQuoteItemRequest item : request.items()) {
            Product product = productRepository.findPublicById(item.productId(), store.getId()).orElse(null);
            BigDecimal quantity = Money.quantity(item.quantity());
            if (product == null) {
                lines.add(unavailableLine(item.productId(), item.variantId(), quantity, "Produto indisponível"));
                continue;
            }
            String issue = validateQuantity(product, quantity);
            ProductVariant variant = null;
            if (issue == null) {
                if (product.hasAvailableVariants()) {
                    if (item.variantId() == null) {
                        issue = "Escolha um sabor/opção";
                    } else {
                        variant = product.getVariants().stream()
                                .filter(candidate -> Objects.equals(candidate.getId(), item.variantId()))
                                .filter(ProductVariant::isAvailable)
                                .findFirst()
                                .orElse(null);
                        if (variant == null) {
                            issue = "Sabor/opção indisponível";
                        }
                    }
                } else if (item.variantId() != null) {
                    issue = "Este produto não possui sabores/opções";
                }
            }
            BigDecimal unitPrice = Money.of(variant != null ? variant.getPrice() : product.getPrice());
            BigDecimal lineTotal = issue == null
                    ? unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            if (issue == null) {
                subtotal = subtotal.add(lineTotal);
            }
            String displayName = variant != null
                    ? product.getName() + " · " + variant.getName()
                    : product.getName();
            lines.add(new CartQuoteLineResponse(
                    product.getId(),
                    variant != null ? variant.getId() : null,
                    displayName,
                    variant != null ? variant.getName() : null,
                    product.getImageUrl(),
                    product.getUnit(),
                    quantity,
                    unitPrice,
                    lineTotal,
                    issue
            ));
        }

        BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String couponCode = null;
        String couponMessage = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            try {
                CouponService.AppliedCoupon applied = couponService.apply(store.getId(), request.couponCode(), subtotal);
                discount = applied.discount();
                if (applied.present()) {
                    couponCode = applied.coupon().getCode();
                    couponMessage = "Cupom aplicado";
                }
            } catch (UnprocessableException ex) {
                couponMessage = ex.getMessage();
            }
        }

        BigDecimal deliveryFee = deliveryService.calculateFee(deliverySettings, fulfillment, subtotal);
        return new CartQuoteResponse(
                lines,
                subtotal,
                discount,
                deliveryFee,
                subtotal.add(deliveryFee).subtract(discount),
                couponCode,
                couponMessage,
                availabilityService.isAcceptingOrders(store),
                deliverySettings.getMinOrderAmount()
        );
    }

    private String validateQuantity(Product product, BigDecimal quantity) {
        if (quantity.compareTo(product.getMinimumQuantity()) < 0) {
            return "Quantidade mínima não atingida";
        }
        if (product.getMaximumQuantity() != null
                && quantity.compareTo(product.getMaximumQuantity()) > 0) {
            return "Quantidade máxima excedida";
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

    private CartQuoteLineResponse unavailableLine(UUID productId, UUID variantId, BigDecimal quantity, String issue) {
        return new CartQuoteLineResponse(
                productId,
                variantId,
                null,
                null,
                null,
                null,
                quantity,
                null,
                BigDecimal.ZERO.setScale(2),
                issue
        );
    }

    private Establishment requireActiveStore(String slug) {
        return establishmentRepository.findBySlug(slug)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Loja não encontrada"));
    }

    private PublicStoreResponse toPublicStore(Establishment establishment) {
        EstablishmentDeliverySettings settings = deliveryService.findOrDefaults(establishment.getId());
        EstablishmentPaymentSettings payment = paymentSettingsRepository.findById(establishment.getId()).orElse(null);
        boolean pixEnabled = payment == null || payment.isPixEnabled();
        return new PublicStoreResponse(
                establishment.getId(),
                establishment.getName(),
                establishment.getSlug(),
                establishment.getLogoUrl(),
                establishment.getCoverUrl(),
                establishment.getDescription(),
                establishment.getPhone(),
                establishment.getAddress(),
                establishment.getCity(),
                establishment.getState(),
                establishment.isActive(),
                availabilityService.isAcceptingOrders(establishment),
                establishment.getStoreOpenMode() == null
                        ? com.sacolao.establishment.entity.StoreOpenMode.AUTO
                        : establishment.getStoreOpenMode(),
                establishment.getTimezone(),
                availabilityService.parseHours(establishment.getOpeningHours()),
                establishment.getRatingAvg(),
                establishment.getRatingCount(),
                DeliveryService.toResponse(settings),
                new PublicPaymentOptionsResponse(pixEnabled, true, true, true)
        );
    }
}
