package com.sacolao.product.dto;

import jakarta.validation.constraints.NotNull;

public record ProductFlagRequest(@NotNull Boolean value) {
}
