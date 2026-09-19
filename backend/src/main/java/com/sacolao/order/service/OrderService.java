package com.sacolao.order.service;

import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.Money;
import com.sacolao.customer.entity.Customer;
import com.sacolao.customer.service.CustomerService;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.order.dto.CheckoutRequest;
import com.sacolao.order.dto.OrderResponse;
import com.sacolao.order.dto.OrderSummaryResponse;
import com.sacolao.order.dto.UpdateOrderStatusRequest;
import com.sacolao.order.entity.FulfillmentType;
import com.sacolao.order.entity.Order;
import com.sacolao.order.entity.OrderItem;
import com.sacolao.order.entity.OrderStatus;
import com.sacolao.order.mapper.OrderMapper;
import com.sacolao.order.repository.OrderRepository;
import com.sacolao.product.entity.Product;
import com.sacolao.product.repository.ProductRepository;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
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

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            EstablishmentRepository establishmentRepository,
            CustomerService customerService
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.establishmentRepository = establishmentRepository;
        this.customerService = customerService;
    }

    @Transactional
    public OrderResponse checkout(String slug, CheckoutRequest request) {
        Establishment store = requireActiveStore(slug);
        validateFulfillment(request);
        Map<UUID, BigDecimal> quantities = mergeQuantities(request.items());
        if (quantities.isEmpty()) {
            throw new UnprocessableException("EMPTY_CART", "Carrinho vazio");
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
        order.setNotes(blankToNull(request.notes()));
        applyAddress(order, request);

        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
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
            item.setProductUnit(product.getUnit());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setSubtotal(lineTotal);
            order.addItem(item);
            subtotal = subtotal.add(lineTotal);
            decrementStock(product, quantity);
        }

        BigDecimal discount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal deliveryFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        order.setSubtotal(subtotal);
        order.setDiscount(discount);
        order.setDeliveryFee(deliveryFee);
        order.setTotal(subtotal.add(deliveryFee).subtract(discount));

        return OrderMapper.toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getPublic(String slug, String publicCode) {
        Establishment store = requireActiveStore(slug);
        return orderRepository.findDetailedByPublicCodeAndEstablishmentId(normalizeCode(publicCode), store.getId())
                .map(OrderMapper::toResponse)
                .orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(OrderStatus status) {
        return orderRepository.findAllDetailedInTenant(TenantContext.require(), status).stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return OrderMapper.toResponse(requireInTenant(id));
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
        order.setStatus(next);
        return OrderMapper.toResponse(order);
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

    private void decrementStock(Product product, BigDecimal quantity) {
        if (!product.isStockControlled() || product.getStockQuantity() == null) {
            return;
        }
        product.setStockQuantity(Money.quantity(product.getStockQuantity().subtract(quantity)));
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
