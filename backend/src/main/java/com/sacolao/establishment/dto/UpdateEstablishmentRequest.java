package com.sacolao.establishment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateEstablishmentRequest(
        @Size(min = 2, max = 160) String name,
        @Size(max = 500) String logoUrl,
        String description,
        @Size(max = 32) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 255) String address,
        @Size(max = 120) String city,
        @Size(max = 2) String state,
        @Size(max = 16) String zipCode
) {
}
