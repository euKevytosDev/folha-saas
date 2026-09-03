package com.sacolao.store.dto;

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
        boolean active
) {
}
