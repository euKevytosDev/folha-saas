package com.sacolao.establishment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEstablishmentRequest(
        @NotBlank @Size(min = 2, max = 160) String name
) {
}
