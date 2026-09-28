package com.sacolao.lead.dto;

import java.time.Instant;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        String name,
        String phone,
        String businessName,
        String brief,
        Instant createdAt
) {
}
