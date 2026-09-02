package com.sacolao.establishment.dto;

import jakarta.validation.constraints.NotNull;

public record EstablishmentStatusRequest(
        @NotNull Boolean active
) {
}
