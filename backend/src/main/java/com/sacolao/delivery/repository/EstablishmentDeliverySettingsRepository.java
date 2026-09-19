package com.sacolao.delivery.repository;

import com.sacolao.delivery.entity.EstablishmentDeliverySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EstablishmentDeliverySettingsRepository extends JpaRepository<EstablishmentDeliverySettings, UUID> {
}
