package com.sacolao.order.mapper;

import com.sacolao.order.dto.OrderResponse;
import com.sacolao.order.entity.Order;
import com.sacolao.order.entity.OrderItem;
import com.sacolao.payment.dto.PaymentResponse;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order) {
        return toResponse(order, null);
    }

    public static OrderResponse toResponse(Order order, PaymentResponse payment) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(OrderMapper::toItem)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getEstablishmentId(),
                order.getCustomer().getId(),
                order.getPublicCode(),
                order.getStatus(),
                order.getFulfillmentType(),
                order.getPaymentMethod(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getCustomerEmail(),
                order.getAddressZipCode(),
                order.getAddressStreet(),
                order.getAddressNumber(),
                order.getAddressComplement(),
                order.getAddressNeighborhood(),
                order.getAddressCity(),
                order.getAddressState(),
                order.getNotes(),
                order.getSubtotal(),
                order.getDiscount(),
                order.getDeliveryFee(),
                order.getTotal(),
                items,
                payment,
                order.getCouponCode(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private static OrderResponse.OrderItemResponse toItem(OrderItem item) {
        return new OrderResponse.OrderItemResponse(
                item.getId(),
                item.getProduct() == null ? null : item.getProduct().getId(),
                item.getProductName(),
                item.getProductUnit(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }
}
