package com.sacolao.fiscal.repository;

import com.sacolao.fiscal.entity.EstablishmentFiscalSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EstablishmentFiscalSettingsRepository extends JpaRepository<EstablishmentFiscalSettings, UUID> {
}
