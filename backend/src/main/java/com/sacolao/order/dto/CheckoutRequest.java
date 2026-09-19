package com.sacolao.order.dto;

import com.sacolao.order.entity.FulfillmentType;
import com.sacolao.order.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotEmpty List<@Valid CheckoutItemRequest> items,
        @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Size(max = 32) String customerPhone,
        @Email @Size(max = 255) String customerEmail,
        @NotNull FulfillmentType fulfillmentType,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 16) String addressZipCode,
        @Size(max = 255) String addressStreet,
        @Size(max = 32) String addressNumber,
        @Size(max = 120) String addressComplement,
        @Size(max = 120) String addressNeighborhood,
        @Size(max = 120) String addressCity,
        @Size(max = 2) String addressState,
        @Size(max = 2000) String notes
) {
    public record CheckoutItemRequest(
            @NotNull UUID productId,
            @NotNull @DecimalMin(value = "0.001") BigDecimal quantity
    ) {
    }
}
