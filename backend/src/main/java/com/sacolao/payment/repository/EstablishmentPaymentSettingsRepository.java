package com.sacolao.payment.repository;

import com.sacolao.payment.entity.EstablishmentPaymentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EstablishmentPaymentSettingsRepository extends JpaRepository<EstablishmentPaymentSettings, UUID> {
}
