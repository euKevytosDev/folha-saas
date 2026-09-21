package com.sacolao.fiscal.dto;

import com.sacolao.fiscal.entity.FiscalEnvironment;

public record FiscalSettingsResponse(
        boolean enabled,
        FiscalEnvironment environment,
        boolean tokenConfigured,
        String cnpj,
        boolean autoEmitOnPaid,
        String defaultNcm,
        String defaultCfop,
        int icmsOrigem,
        String icmsSituacaoTributaria
) {
}
