package com.sacolao.store.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartQuoteItemRequest(
        @NotNull UUID productId,
        UUID variantId,
        List<UUID> variantIds,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity
) {
}
