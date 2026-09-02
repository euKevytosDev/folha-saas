package com.sacolao.establishment.mapper;

import com.sacolao.establishment.dto.EstablishmentResponse;
import com.sacolao.establishment.entity.Establishment;

public final class EstablishmentMapper {

    private EstablishmentMapper() {
    }

    public static EstablishmentResponse toResponse(Establishment establishment) {
        return new EstablishmentResponse(
                establishment.getId(),
                establishment.getName(),
                establishment.getSlug(),
                establishment.getLogoUrl(),
                establishment.getDescription(),
                establishment.getPhone(),
                establishment.getEmail(),
                establishment.getAddress(),
                establishment.getCity(),
                establishment.getState(),
                establishment.getZipCode(),
                establishment.getPlanCode(),
                establishment.isActive(),
                establishment.getCreatedAt(),
                establishment.getUpdatedAt()
        );
    }
}
