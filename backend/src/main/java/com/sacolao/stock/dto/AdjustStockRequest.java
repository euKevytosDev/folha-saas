package com.sacolao.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdjustStockRequest(
        @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal quantity,
        @Size(max = 255) String note
) {
}
