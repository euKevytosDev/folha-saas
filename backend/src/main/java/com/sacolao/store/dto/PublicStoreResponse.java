package com.sacolao.store.dto;

import com.sacolao.delivery.dto.DeliverySettingsResponse;

import java.util.UUID;

public record PublicStoreResponse(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String description,
        String phone,
        String address,
        String city,
        String state,
        boolean active,
        DeliverySettingsResponse delivery
) {
}
