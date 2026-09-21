package com.sacolao.fiscal.mapper;

import com.sacolao.fiscal.dto.FiscalSettingsResponse;
import com.sacolao.fiscal.dto.NfceInfoResponse;
import com.sacolao.fiscal.entity.EstablishmentFiscalSettings;
import com.sacolao.fiscal.entity.NfceStatus;
import com.sacolao.order.entity.Order;

public final class FiscalMapper {

    private FiscalMapper() {
    }

    public static FiscalSettingsResponse toSettingsResponse(EstablishmentFiscalSettings settings) {
        return new FiscalSettingsResponse(
                settings.isEnabled(),
                settings.getEnvironment(),
                settings.getApiToken() != null && !settings.getApiToken().isBlank(),
                settings.getCnpj(),
                settings.isAutoEmitOnPaid(),
                settings.getDefaultNcm(),
                settings.getDefaultCfop(),
                settings.getIcmsOrigem(),
                settings.getIcmsSituacaoTributaria()
        );
    }

    public static NfceInfoResponse toNfceInfo(Order order) {
        NfceStatus status = order.getNfceStatus() == null ? NfceStatus.NONE : order.getNfceStatus();
        return new NfceInfoResponse(
                order.getNfceRef(),
                status,
                order.getNfceNumber(),
                order.getNfceSeries(),
                order.getNfceChave(),
                order.getNfceUrlDanfe(),
                order.getNfceUrlXml(),
                order.getNfceQrcodeUrl(),
                order.getNfceErrorMessage(),
                order.getNfceEmittedAt()
        );
    }
}
