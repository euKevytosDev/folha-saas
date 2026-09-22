package com.sacolao.order.dto;

import com.sacolao.fiscal.dto.NfceInfoResponse;
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
        String viewToken,
        OrderStatus status,
        FulfillmentType fulfillmentType,
        PaymentMethod paymentMethod,
        String customerName,
        String customerPhone,
        String customerEmail,
        String customerCpf,
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
        NfceInfoResponse nfce,
        String couponCode,
        Instant createdAt,
        Instant updatedAt
) {
    public record OrderItemResponse(
            UUID id,
            UUID productId,
            String productName,
            String imageUrl,
            ProductUnit productUnit,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}
