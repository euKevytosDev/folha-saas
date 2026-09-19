package com.sacolao.delivery.service;

import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.common.util.Money;
import com.sacolao.delivery.dto.DeliverySettingsResponse;
import com.sacolao.delivery.dto.UpdateDeliverySettingsRequest;
import com.sacolao.delivery.entity.EstablishmentDeliverySettings;
import com.sacolao.delivery.repository.EstablishmentDeliverySettingsRepository;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.order.entity.FulfillmentType;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class DeliveryService {

    private final EstablishmentDeliverySettingsRepository settingsRepository;
    private final EstablishmentRepository establishmentRepository;

    public DeliveryService(
            EstablishmentDeliverySettingsRepository settingsRepository,
            EstablishmentRepository establishmentRepository
    ) {
        this.settingsRepository = settingsRepository;
        this.establishmentRepository = establishmentRepository;
    }

    @Transactional(readOnly = true)
    public DeliverySettingsResponse getSettings() {
        return toResponse(findOrDefaults(requireTenantEstablishment().getId()));
    }

    @Transactional
    public DeliverySettingsResponse updateSettings(UpdateDeliverySettingsRequest request) {
        Establishment establishment = requireTenantEstablishment();
        EstablishmentDeliverySettings settings = settingsRepository.findById(establishment.getId())
                .orElseGet(() -> {
                    EstablishmentDeliverySettings created = new EstablishmentDeliverySettings();
                    created.setEstablishmentId(establishment.getId());
                    return created;
                });
        settings.setDeliveryEnabled(Boolean.TRUE.equals(request.deliveryEnabled()));
        settings.setPickupEnabled(Boolean.TRUE.equals(request.pickupEnabled()));
        settings.setFixedFee(Money.of(request.fixedFee()));
        settings.setFreeAboveAmount(request.freeAboveAmount() == null ? null : Money.of(request.freeAboveAmount()));
        settings.setEstimatedMinutes(request.estimatedMinutes());
        if (!settings.isDeliveryEnabled() && !settings.isPickupEnabled()) {
            throw new UnprocessableException("FULFILLMENT_REQUIRED", "Habilite entrega ou retirada");
        }
        return toResponse(settingsRepository.save(settings));
    }

    @Transactional
    public EstablishmentDeliverySettings requireSettings(UUID establishmentId) {
        return settingsRepository.findById(establishmentId).orElseGet(() -> {
            EstablishmentDeliverySettings defaults = defaultsFor(establishmentId);
            return settingsRepository.save(defaults);
        });
    }

    @Transactional(readOnly = true)
    public EstablishmentDeliverySettings findOrDefaults(UUID establishmentId) {
        return settingsRepository.findById(establishmentId).orElseGet(() -> defaultsFor(establishmentId));
    }

    private static EstablishmentDeliverySettings defaultsFor(UUID establishmentId) {
        EstablishmentDeliverySettings defaults = new EstablishmentDeliverySettings();
        defaults.setEstablishmentId(establishmentId);
        defaults.setDeliveryEnabled(true);
        defaults.setPickupEnabled(true);
        defaults.setFixedFee(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return defaults;
    }

    public void assertFulfillmentAllowed(EstablishmentDeliverySettings settings, FulfillmentType type) {
        if (type == FulfillmentType.DELIVERY && !settings.isDeliveryEnabled()) {
            throw new UnprocessableException("DELIVERY_DISABLED", "Esta loja não faz entrega no momento");
        }
        if (type == FulfillmentType.PICKUP && !settings.isPickupEnabled()) {
            throw new UnprocessableException("PICKUP_DISABLED", "Esta loja não aceita retirada no momento");
        }
    }

    public BigDecimal calculateFee(EstablishmentDeliverySettings settings, FulfillmentType type, BigDecimal subtotal) {
        if (type != FulfillmentType.DELIVERY) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal fee = Money.of(settings.getFixedFee());
        if (settings.getFreeAboveAmount() != null
                && subtotal.compareTo(Money.of(settings.getFreeAboveAmount())) >= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return fee;
    }

    public static DeliverySettingsResponse toResponse(EstablishmentDeliverySettings settings) {
        return new DeliverySettingsResponse(
                settings.isDeliveryEnabled(),
                settings.isPickupEnabled(),
                settings.getFixedFee(),
                settings.getFreeAboveAmount(),
                settings.getEstimatedMinutes()
        );
    }

    private Establishment requireTenantEstablishment() {
        return establishmentRepository.findById(TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }
}
