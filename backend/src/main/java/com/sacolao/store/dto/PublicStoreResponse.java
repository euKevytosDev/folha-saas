package com.sacolao.store.dto;

import com.sacolao.delivery.dto.DeliverySettingsResponse;
import com.sacolao.establishment.dto.OpeningInterval;
import com.sacolao.establishment.entity.StoreOpenMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PublicStoreResponse(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String coverUrl,
        String description,
        String phone,
        String address,
        String city,
        String state,
        boolean active,
        boolean acceptingOrders,
        StoreOpenMode storeOpenMode,
        String timezone,
        Map<String, List<OpeningInterval>> openingHours,
        BigDecimal ratingAvg,
        int ratingCount,
        DeliverySettingsResponse delivery,
        PublicPaymentOptionsResponse payments
) {
}
