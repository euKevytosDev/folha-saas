package com.sacolao.delivery.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateDeliverySettingsRequest(
        @NotNull Boolean deliveryEnabled,
        @NotNull Boolean pickupEnabled,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal fixedFee,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal freeAboveAmount,
        @Min(0) Integer estimatedMinutes
) {
}
