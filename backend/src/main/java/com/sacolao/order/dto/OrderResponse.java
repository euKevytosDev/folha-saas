package com.sacolao.order.dto;

import com.sacolao.order.entity.FulfillmentType;
import com.sacolao.order.entity.OrderStatus;
import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.payment.dto.PaymentResponse;
import com.sacolao.product.entity.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID establishmentId,
        UUID customerId,
        String publicCode,
        OrderStatus status,
        FulfillmentType fulfillmentType,
        PaymentMethod paymentMethod,
        String customerName,
        String customerPhone,
        String customerEmail,
        String addressZipCode,
        String addressStreet,
        String addressNumber,
        String addressComplement,
        String addressNeighborhood,
        String addressCity,
        String addressState,
        String notes,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal deliveryFee,
        BigDecimal total,
        List<OrderItemResponse> items,
        PaymentResponse payment,
        Instant createdAt,
        Instant updatedAt
) {
    public record OrderItemResponse(
            UUID id,
            UUID productId,
            String productName,
            ProductUnit productUnit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}
