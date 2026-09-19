package com.sacolao.delivery.dto;

import java.math.BigDecimal;

public record DeliverySettingsResponse(
        boolean deliveryEnabled,
        boolean pickupEnabled,
        BigDecimal fixedFee,
        BigDecimal freeAboveAmount,
        Integer estimatedMinutes,
        BigDecimal minOrderAmount,
        Integer pickupEtaMinutes,
        Integer deliveryEtaMinutes
) {
}
