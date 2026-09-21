package com.sacolao.fiscal.dto;

import com.sacolao.fiscal.entity.FiscalEnvironment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateFiscalSettingsRequest(
        @NotNull Boolean enabled,
        @NotNull FiscalEnvironment environment,
        @Size(max = 500) String apiToken,
        @Size(max = 18) String cnpj,
        Boolean autoEmitOnPaid,
        @Size(max = 8) String defaultNcm,
        @Size(max = 4) String defaultCfop,
        Integer icmsOrigem,
        @Size(max = 4) String icmsSituacaoTributaria
) {
}
