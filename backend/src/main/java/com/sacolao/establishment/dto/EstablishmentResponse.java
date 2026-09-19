package com.sacolao.establishment.dto;

import com.sacolao.establishment.entity.PlanCode;
import com.sacolao.establishment.entity.StoreOpenMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EstablishmentResponse(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String coverUrl,
        String description,
        String phone,
        String email,
        String address,
        String city,
        String state,
        String zipCode,
        PlanCode planCode,
        boolean active,
        StoreOpenMode storeOpenMode,
        boolean acceptingOrders,
        String timezone,
        Map<String, List<OpeningInterval>> openingHours,
        BigDecimal ratingAvg,
        int ratingCount,
        Instant createdAt,
        Instant updatedAt
) {
}
