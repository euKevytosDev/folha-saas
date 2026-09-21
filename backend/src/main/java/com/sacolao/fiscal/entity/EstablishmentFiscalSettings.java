package com.sacolao.fiscal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "establishment_fiscal_settings")
public class EstablishmentFiscalSettings {

    @Id
    @Column(name = "establishment_id")
    private UUID establishmentId;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FiscalEnvironment environment = FiscalEnvironment.HOMOLOG;

    @Column(name = "api_token", columnDefinition = "text")
    private String apiToken;

    @Column(length = 14)
    private String cnpj;

    @Column(name = "auto_emit_on_paid", nullable = false)
    private boolean autoEmitOnPaid = true;

    @Column(name = "default_ncm", nullable = false, length = 8)
    private String defaultNcm = "21069090";

    @Column(name = "default_cfop", nullable = false, length = 4)
    private String defaultCfop = "5102";

    @Column(name = "icms_origem", nullable = false)
    private int icmsOrigem;

    @Column(name = "icms_situacao_tributaria", nullable = false, length = 4)
    private String icmsSituacaoTributaria = "102";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isReadyToEmit() {
        return enabled
                && apiToken != null
                && !apiToken.isBlank()
                && cnpj != null
                && cnpj.replaceAll("\\D", "").length() == 14;
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public void setEstablishmentId(UUID establishmentId) {
        this.establishmentId = establishmentId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FiscalEnvironment getEnvironment() {
        return environment;
    }

    public void setEnvironment(FiscalEnvironment environment) {
        this.environment = environment;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public boolean isAutoEmitOnPaid() {
        return autoEmitOnPaid;
    }

    public void setAutoEmitOnPaid(boolean autoEmitOnPaid) {
        this.autoEmitOnPaid = autoEmitOnPaid;
    }

    public String getDefaultNcm() {
        return defaultNcm;
    }

    public void setDefaultNcm(String defaultNcm) {
        this.defaultNcm = defaultNcm;
    }

    public String getDefaultCfop() {
        return defaultCfop;
    }

    public void setDefaultCfop(String defaultCfop) {
        this.defaultCfop = defaultCfop;
    }

    public int getIcmsOrigem() {
        return icmsOrigem;
    }

    public void setIcmsOrigem(int icmsOrigem) {
        this.icmsOrigem = icmsOrigem;
    }

    public String getIcmsSituacaoTributaria() {
        return icmsSituacaoTributaria;
    }

    public void setIcmsSituacaoTributaria(String icmsSituacaoTributaria) {
        this.icmsSituacaoTributaria = icmsSituacaoTributaria;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
