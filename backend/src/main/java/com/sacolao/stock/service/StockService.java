package com.sacolao.stock.service;

import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.Money;
import com.sacolao.order.entity.Order;
import com.sacolao.product.dto.ProductResponse;
import com.sacolao.product.entity.Product;
import com.sacolao.product.mapper.ProductMapper;
import com.sacolao.product.repository.ProductRepository;
import com.sacolao.stock.dto.AdjustStockRequest;
import com.sacolao.stock.entity.StockMovement;
import com.sacolao.stock.entity.StockMovementType;
import com.sacolao.stock.repository.StockMovementRepository;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class StockService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public StockService(ProductRepository productRepository, StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public ProductResponse adjust(UUID productId, AdjustStockRequest request) {
        Product product = productRepository.findByIdAndEstablishment_Id(productId, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
        if (!product.isStockControlled()) {
            product.setStockControlled(true);
        }
        BigDecimal target = Money.quantity(request.quantity());
        BigDecimal current = product.getStockQuantity() == null
                ? BigDecimal.ZERO
                : Money.quantity(product.getStockQuantity());
        BigDecimal delta = target.subtract(current);
        product.setStockQuantity(target);
        if (target.compareTo(BigDecimal.ZERO) <= 0) {
            product.setAvailable(false);
        }
        recordMovement(
                product,
                null,
                delta.compareTo(BigDecimal.ZERO) >= 0 ? StockMovementType.RESTOCK : StockMovementType.ADJUSTMENT,
                delta,
                target,
                request.note()
        );
        return ProductMapper.toResponse(product);
    }

    @Transactional
    public void consumeForSale(Product product, Order order, BigDecimal quantity) {
        if (!product.isStockControlled() || product.getStockQuantity() == null) {
            return;
        }
        BigDecimal current = Money.quantity(product.getStockQuantity());
        BigDecimal qty = Money.quantity(quantity);
        if (qty.compareTo(current) > 0) {
            throw new UnprocessableException("OUT_OF_STOCK", "Estoque insuficiente para " + product.getName());
        }
        BigDecimal after = Money.quantity(current.subtract(qty));
        product.setStockQuantity(after);
        if (after.compareTo(BigDecimal.ZERO) <= 0) {
            product.setAvailable(false);
        }
        recordMovement(product, order, StockMovementType.SALE, qty.negate(), after, "Venda " + order.getPublicCode());
    }

    private void recordMovement(
            Product product,
            Order order,
            StockMovementType type,
            BigDecimal delta,
            BigDecimal after,
            String note
    ) {
        StockMovement movement = new StockMovement();
        movement.setEstablishment(product.getEstablishment());
        movement.setProduct(product);
        movement.setOrder(order);
        movement.setMovementType(type);
        movement.setQuantityDelta(Money.quantity(delta));
        movement.setQuantityAfter(after);
        movement.setNote(note == null || note.isBlank() ? null : note.trim());
        stockMovementRepository.save(movement);
    }
}
