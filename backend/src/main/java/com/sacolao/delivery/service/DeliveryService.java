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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        Integer pickupEta = request.pickupEtaMinutes() != null ? request.pickupEtaMinutes() : request.estimatedMinutes();
        Integer deliveryEta = request.deliveryEtaMinutes() != null ? request.deliveryEtaMinutes() : request.estimatedMinutes();
        settings.setPickupEtaMinutes(pickupEta);
        settings.setDeliveryEtaMinutes(deliveryEta);
        settings.setEstimatedMinutes(deliveryEta != null ? deliveryEta : pickupEta);
        settings.setMinOrderAmount(request.minOrderAmount() == null ? null : Money.of(request.minOrderAmount()));
        if (request.collectEmail() != null) {
            settings.setCollectEmail(Boolean.TRUE.equals(request.collectEmail()));
        }
        if (request.deliveryNeighborhoods() != null) {
            List<String> neighborhoods = parseNeighborhoods(request.deliveryNeighborhoods());
            settings.setDeliveryNeighborhoods(neighborhoods.isEmpty() ? null : String.join("\n", neighborhoods));
        }
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

    public void assertMinOrder(EstablishmentDeliverySettings settings, BigDecimal subtotal) {
        if (settings.getMinOrderAmount() == null) {
            return;
        }
        BigDecimal min = Money.of(settings.getMinOrderAmount());
        if (min.compareTo(BigDecimal.ZERO) > 0 && Money.of(subtotal).compareTo(min) < 0) {
            throw new UnprocessableException(
                    "MIN_ORDER",
                    "Pedido mínimo de R$ " + min.toPlainString().replace('.', ',')
            );
        }
    }

    public static DeliverySettingsResponse toResponse(EstablishmentDeliverySettings settings) {
        Integer pickup = settings.getPickupEtaMinutes() != null
                ? settings.getPickupEtaMinutes()
                : settings.getEstimatedMinutes();
        Integer delivery = settings.getDeliveryEtaMinutes() != null
                ? settings.getDeliveryEtaMinutes()
                : settings.getEstimatedMinutes();
        return new DeliverySettingsResponse(
                settings.isDeliveryEnabled(),
                settings.isPickupEnabled(),
                settings.getFixedFee(),
                settings.getFreeAboveAmount(),
                settings.getEstimatedMinutes(),
                settings.getMinOrderAmount(),
                pickup,
                delivery,
                settings.isCollectEmail(),
                parseNeighborhoods(settings.getDeliveryNeighborhoods())
        );
    }

    public boolean hasNeighborhoodList(EstablishmentDeliverySettings settings) {
        return !parseNeighborhoods(settings.getDeliveryNeighborhoods()).isEmpty();
    }

    public String canonicalNeighborhood(EstablishmentDeliverySettings settings, String chosen) {
        String trimmed = chosen == null ? "" : chosen.trim().replaceAll("\\s+", " ");
        List<String> allowed = parseNeighborhoods(settings.getDeliveryNeighborhoods());
        if (allowed.isEmpty()) {
            return trimmed.isEmpty() ? null : trimmed;
        }
        String key = fold(trimmed);
        for (String item : allowed) {
            if (fold(item).equals(key)) {
                return item;
            }
        }
        return null;
    }

    public static List<String> parseNeighborhoods(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim().replaceAll("\\s+", " ");
            if (trimmed.isEmpty() || trimmed.length() > 120) {
                continue;
            }
            boolean duplicate = false;
            String key = fold(trimmed);
            for (String existing : seen) {
                if (fold(existing).equals(key)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                seen.add(trimmed);
            }
            if (seen.size() >= 150) {
                break;
            }
        }
        return List.copyOf(new ArrayList<>(seen));
    }

    private static String fold(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    private Establishment requireTenantEstablishment() {
        return establishmentRepository.findById(TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }
}
