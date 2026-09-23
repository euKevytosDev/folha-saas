package com.sacolao.order.service;

import com.sacolao.common.exception.ConflictException;
import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.CpfValidator;
import com.sacolao.common.util.Money;
import com.sacolao.coupon.entity.Coupon;
import com.sacolao.coupon.repository.CouponRepository;
import com.sacolao.coupon.service.CouponService;
import com.sacolao.customer.entity.Customer;
import com.sacolao.customer.service.CustomerService;
import com.sacolao.delivery.entity.EstablishmentDeliverySettings;
import com.sacolao.delivery.service.DeliveryService;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.establishment.service.StoreAvailabilityService;
import com.sacolao.order.dto.CheckoutRequest;
import com.sacolao.order.dto.OrderResponse;
import com.sacolao.order.dto.OrderSummaryResponse;
import com.sacolao.order.dto.UpdateOrderStatusRequest;
import com.sacolao.order.entity.FulfillmentType;
import com.sacolao.order.entity.Order;
import com.sacolao.order.entity.OrderItem;
import com.sacolao.order.entity.OrderStatus;
import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.order.mapper.OrderMapper;
import com.sacolao.order.repository.OrderRepository;
import com.sacolao.payment.dto.PaymentResponse;
import com.sacolao.payment.entity.EstablishmentPaymentSettings;
import com.sacolao.payment.entity.IdempotencyKey;
import com.sacolao.payment.entity.Payment;
import com.sacolao.payment.mapper.PaymentMapper;
import com.sacolao.payment.repository.EstablishmentPaymentSettingsRepository;
import com.sacolao.payment.repository.PaymentRepository;
import com.sacolao.payment.service.IdempotencyService;
import com.sacolao.payment.service.PaymentService;
import com.sacolao.product.entity.Product;
import com.sacolao.product.repository.ProductRepository;
import com.sacolao.stock.service.StockService;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final EstablishmentRepository establishmentRepository;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final DeliveryService deliveryService;
    private final CouponService couponService;
    private final StockService stockService;
    private final StoreAvailabilityService availabilityService;
    private final EstablishmentPaymentSettingsRepository paymentSettingsRepository;
    private final CouponRepository couponRepository;
    private final JsonMapper jsonMapper;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            EstablishmentRepository establishmentRepository,
            CustomerService customerService,
            PaymentService paymentService,
            PaymentRepository paymentRepository,
            IdempotencyService idempotencyService,
            DeliveryService deliveryService,
            CouponService couponService,
            StockService stockService,
            StoreAvailabilityService availabilityService,
            EstablishmentPaymentSettingsRepository paymentSettingsRepository,
            CouponRepository couponRepository,
            JsonMapper jsonMapper
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.establishmentRepository = establishmentRepository;
        this.customerService = customerService;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.deliveryService = deliveryService;
        this.couponService = couponService;
        this.stockService = stockService;
        this.availabilityService = availabilityService;
        this.paymentSettingsRepository = paymentSettingsRepository;
        this.couponRepository = couponRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public OrderResponse checkout(String slug, CheckoutRequest request, String idempotencyKey) {
        Establishment store = requireActiveStore(slug);
        if (!availabilityService.isAcceptingOrders(store)) {
            throw new UnprocessableException("STORE_CLOSED", "Loja fechada no momento");
        }
        EstablishmentDeliverySettings deliverySettings = deliveryService.requireSettings(store.getId());
        deliveryService.assertFulfillmentAllowed(deliverySettings, request.fulfillmentType());
        validateFulfillment(request);
        validatePaymentMethod(store.getId(), request.paymentMethod());
        validateCustomerCpf(request);
        Map<UUID, BigDecimal> quantities = mergeQuantities(request.items());
        if (quantities.isEmpty()) {
            throw new UnprocessableException("EMPTY_CART", "Carrinho vazio");
        }

        String requestPayload = toJson(request);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyService.find(store.getId(), IdempotencyService.SCOPE_CHECKOUT, idempotencyKey);
            if (existing.isPresent()) {
                IdempotencyKey key = existing.get();
                if (!key.getRequestHash().equals(IdempotencyService.sha256(requestPayload))) {
                    throw new ConflictException("IDEMPOTENCY_CONFLICT", "Chave de idempotência reutilizada com payload diferente");
                }
                if (key.getResourceId() != null) {
                    return toResponse(requireById(key.getResourceId(), store.getId()));
                }
            }
        }

        Customer customer = customerService.findOrCreate(
                store,
                request.customerName(),
                request.customerPhone(),
                request.customerEmail()
        );

        Order order = new Order();
        order.setEstablishment(store);
        order.setCustomer(customer);
        order.setPublicCode(nextPublicCode());
        order.setStatus(OrderStatus.PENDING);
        order.setFulfillmentType(request.fulfillmentType());
        order.setPaymentMethod(request.paymentMethod());
        order.setCustomerName(request.customerName().trim());
        order.setCustomerPhone(CustomerService.normalizePhone(request.customerPhone()));
        order.setCustomerEmail(blankToNull(request.customerEmail()));
        order.setCustomerCpf(normalizeCpf(request.customerCpf()));
        order.setViewToken(nextViewToken());
        order.setNotes(blankToNull(request.notes()));
        applyAddress(order, request);

        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map<Product, BigDecimal> stockConsumptions = new LinkedHashMap<>();
        for (Map.Entry<UUID, BigDecimal> entry : quantities.entrySet()) {
            Product product = productRepository.findPublicById(entry.getKey(), store.getId())
                    .orElseThrow(() -> new UnprocessableException("PRODUCT_UNAVAILABLE", "Produto indisponível"));
            BigDecimal quantity = Money.quantity(entry.getValue());
            validateQuantity(product, quantity);
            BigDecimal unitPrice = Money.of(product.getPrice());
            BigDecimal lineTotal = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setImageUrl(product.getImageUrl());
            item.setProductUnit(product.getUnit());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setSubtotal(lineTotal);
            order.addItem(item);
            subtotal = subtotal.add(lineTotal);
            stockConsumptions.put(product, quantity);
        }

        deliveryService.assertMinOrder(deliverySettings, subtotal, request.fulfillmentType());

        CouponService.AppliedCoupon applied = couponService.apply(store.getId(), request.couponCode(), subtotal);
        BigDecimal discount = applied.discount();
        BigDecimal deliveryFee = deliveryService.calculateFee(deliverySettings, request.fulfillmentType(), subtotal);
        order.setSubtotal(subtotal);
        order.setDiscount(discount);
        order.setDeliveryFee(deliveryFee);
        order.setTotal(subtotal.add(deliveryFee).subtract(discount));
        if (applied.present()) {
            order.setCouponId(applied.coupon().getId());
            order.setCouponCode(applied.coupon().getCode());
        }

        Order saved = orderRepository.save(order);
        for (Map.Entry<Product, BigDecimal> entry : stockConsumptions.entrySet()) {
            stockService.consumeForSale(entry.getKey(), saved, entry.getValue());
        }
        if (applied.present()) {
            couponService.markUsed(applied.coupon());
        }
        Payment payment = paymentService.createForOrder(saved, idempotencyKey);
        OrderResponse response = OrderMapper.toResponse(saved, PaymentMapper.toResponse(payment));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.save(
                    store,
                    IdempotencyService.SCOPE_CHECKOUT,
                    idempotencyKey,
                    requestPayload,
                    toJson(response),
                    saved.getId(),
                    201
            );
        }
        return response;
    }

    @Transactional(readOnly = true)
    public OrderResponse getPublic(String slug, String publicCode, String viewToken) {
        Establishment store = requireActiveStore(slug);
        Order order = orderRepository.findDetailedByPublicCodeAndEstablishmentId(normalizeCode(publicCode), store.getId())
                .orElseThrow(this::notFound);
        assertViewToken(order, viewToken);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status) {
        return orderRepository.findAllDetailedInTenant(TenantContext.require(), status).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return toResponse(requireInTenant(id));
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, UpdateOrderStatusRequest request) {
        Order order = requireInTenant(id);
        OrderStatus next = request.status();
        if (!order.getStatus().canTransitionTo(next)) {
            throw new UnprocessableException(
                    "INVALID_STATUS_TRANSITION",
                    "Não é possível mudar de " + order.getStatus() + " para " + next
            );
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(next);
        if (next == OrderStatus.CANCELLED && previous != OrderStatus.CANCELLED) {
            stockService.restoreForCancelledOrder(order);
            if (order.getCouponId() != null) {
                couponRepository.findById(order.getCouponId()).ifPresent(couponService::releaseUsed);
            }
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderSummaryResponse summary() {
        UUID tenantId = TenantContext.require();
        return new OrderSummaryResponse(
                orderRepository.countByEstablishment_Id(tenantId),
                orderRepository.countByEstablishment_IdAndStatus(tenantId, OrderStatus.PENDING),
                orderRepository.countByEstablishment_IdAndStatus(tenantId, OrderStatus.CONFIRMED),
                orderRepository.countByEstablishment_IdAndStatus(tenantId, OrderStatus.PREPARING),
                orderRepository.countByEstablishment_IdAndStatus(tenantId, OrderStatus.DISPATCHED),
                orderRepository.countByEstablishment_IdAndStatus(tenantId, OrderStatus.DELIVERED),
                orderRepository.countByEstablishment_IdAndStatus(tenantId, OrderStatus.CANCELLED)
        );
    }

    private OrderResponse toResponse(Order order) {
        PaymentResponse payment = paymentRepository.findByOrder_IdAndEstablishment_Id(order.getId(), order.getEstablishmentId())
                .map(PaymentMapper::toResponse)
                .orElse(null);
        return OrderMapper.toResponse(order, payment);
    }

    private Order requireById(UUID id, UUID establishmentId) {
        return orderRepository.findDetailedByIdAndEstablishmentId(id, establishmentId)
                .orElseThrow(this::notFound);
    }

    private Order requireInTenant(UUID id) {
        return orderRepository.findDetailedByIdAndEstablishmentId(id, TenantContext.require())
                .orElseThrow(this::notFound);
    }

    private Establishment requireActiveStore(String slug) {
        return establishmentRepository.findBySlug(slug)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Loja não encontrada"));
    }

    private void validateFulfillment(CheckoutRequest request) {
        if (request.fulfillmentType() == FulfillmentType.DELIVERY) {
            if (isBlank(request.addressStreet())
                    || isBlank(request.addressNumber())
                    || isBlank(request.addressNeighborhood())
                    || isBlank(request.addressCity())
                    || isBlank(request.addressState())) {
                throw new UnprocessableException("ADDRESS_REQUIRED", "Endereço completo é obrigatório para entrega");
            }
            if (request.addressState().trim().length() != 2) {
                throw new UnprocessableException("INVALID_STATE", "UF inválida");
            }
        }
    }

    private void applyAddress(Order order, CheckoutRequest request) {
        if (request.fulfillmentType() == FulfillmentType.PICKUP) {
            return;
        }
        order.setAddressZipCode(blankToNull(request.addressZipCode()));
        order.setAddressStreet(request.addressStreet().trim());
        order.setAddressNumber(request.addressNumber().trim());
        order.setAddressComplement(blankToNull(request.addressComplement()));
        order.setAddressNeighborhood(request.addressNeighborhood().trim());
        order.setAddressCity(request.addressCity().trim());
        order.setAddressState(request.addressState().trim().toUpperCase(Locale.ROOT));
    }

    private Map<UUID, BigDecimal> mergeQuantities(List<CheckoutRequest.CheckoutItemRequest> items) {
        Map<UUID, BigDecimal> quantities = new LinkedHashMap<>();
        for (CheckoutRequest.CheckoutItemRequest item : items) {
            BigDecimal quantity = Money.quantity(item.quantity());
            quantities.merge(item.productId(), quantity, BigDecimal::add);
        }
        return quantities;
    }

    private void validateQuantity(Product product, BigDecimal quantity) {
        if (quantity.compareTo(product.getMinimumQuantity()) < 0) {
            throw new UnprocessableException("MIN_QUANTITY", "Quantidade mínima não atingida para " + product.getName());
        }
        if (!product.getUnit().decimalAllowed() && quantity.stripTrailingZeros().scale() > 0) {
            throw new UnprocessableException("DECIMAL_NOT_ALLOWED", "Unidade de " + product.getName() + " não aceita decimal");
        }
        if (product.isStockControlled()
                && product.getStockQuantity() != null
                && quantity.compareTo(product.getStockQuantity()) > 0) {
            throw new UnprocessableException("OUT_OF_STOCK", "Estoque insuficiente para " + product.getName());
        }
    }

    private void validatePaymentMethod(UUID establishmentId, PaymentMethod method) {
        EstablishmentPaymentSettings settings = paymentSettingsRepository.findById(establishmentId).orElse(null);
        if (method == PaymentMethod.PIX) {
            if (settings != null && !settings.isPixEnabled()) {
                throw new UnprocessableException("PIX_DISABLED", "PIX não está habilitado nesta loja");
            }
        }
    }

    private void validateCustomerCpf(CheckoutRequest request) {
        String digits = CpfValidator.onlyDigits(request.customerCpf());
        if (request.paymentMethod() == PaymentMethod.PIX) {
            if (digits.isBlank()) {
                throw new UnprocessableException("CPF_REQUIRED", "Informe um CPF válido para pagar com PIX");
            }
            if (!CpfValidator.isValid(digits)) {
                throw new UnprocessableException("INVALID_CPF", "CPF inválido");
            }
            return;
        }
        if (!digits.isBlank() && !CpfValidator.isValid(digits)) {
            throw new UnprocessableException("INVALID_CPF", "CPF inválido");
        }
    }

    private void assertViewToken(Order order, String viewToken) {
        if (viewToken == null || viewToken.isBlank() || order.getViewToken() == null
                || !order.getViewToken().equals(viewToken.trim())) {
            throw notFound();
        }
    }

    private String nextViewToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String normalizeCpf(String value) {
        String digits = CpfValidator.onlyDigits(value);
        return digits.isBlank() ? null : digits;
    }

    private String nextPublicCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder("F");
            for (int i = 0; i < 7; i++) {
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!orderRepository.existsByPublicCode(candidate)) {
                return candidate;
            }
        }
        throw new UnprocessableException("CODE_GENERATION_FAILED", "Não foi possível gerar código do pedido");
    }

    private String normalizeCode(String publicCode) {
        return publicCode == null ? "" : publicCode.trim().toUpperCase(Locale.ROOT);
    }

    private String toJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            return String.valueOf(value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Recurso não encontrado");
    }
}
