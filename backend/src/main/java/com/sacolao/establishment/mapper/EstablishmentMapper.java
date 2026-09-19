package com.sacolao.establishment.mapper;

import com.sacolao.establishment.dto.EstablishmentResponse;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.service.StoreAvailabilityService;

public final class EstablishmentMapper {

    private EstablishmentMapper() {
    }

    public static EstablishmentResponse toResponse(Establishment establishment, StoreAvailabilityService availability) {
        return new EstablishmentResponse(
                establishment.getId(),
                establishment.getName(),
                establishment.getSlug(),
                establishment.getLogoUrl(),
                establishment.getCoverUrl(),
                establishment.getDescription(),
                establishment.getPhone(),
                establishment.getEmail(),
                establishment.getAddress(),
                establishment.getCity(),
                establishment.getState(),
                establishment.getZipCode(),
                establishment.getPlanCode(),
                establishment.isActive(),
                establishment.getStoreOpenMode() == null
                        ? com.sacolao.establishment.entity.StoreOpenMode.AUTO
                        : establishment.getStoreOpenMode(),
                availability.isAcceptingOrders(establishment),
                establishment.getTimezone(),
                availability.parseHours(establishment.getOpeningHours()),
                establishment.getRatingAvg(),
                establishment.getRatingCount(),
                establishment.getCreatedAt(),
                establishment.getUpdatedAt()
        );
    }
}
