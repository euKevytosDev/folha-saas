package com.sacolao.fiscal.service;

import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.fiscal.dto.FiscalConnectionTestResponse;
import com.sacolao.fiscal.dto.FiscalSettingsResponse;
import com.sacolao.fiscal.dto.NfceInfoResponse;
import com.sacolao.fiscal.dto.UpdateFiscalSettingsRequest;
import com.sacolao.fiscal.entity.EstablishmentFiscalSettings;
import com.sacolao.fiscal.entity.FiscalEnvironment;
import com.sacolao.fiscal.entity.NfceStatus;
import com.sacolao.fiscal.focus.FocusNfeClient;
import com.sacolao.fiscal.mapper.FiscalMapper;
import com.sacolao.fiscal.repository.EstablishmentFiscalSettingsRepository;
import com.sacolao.order.entity.FulfillmentType;
import com.sacolao.order.entity.Order;
import com.sacolao.order.entity.OrderItem;
import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.order.repository.OrderRepository;
import com.sacolao.product.entity.ProductUnit;
import com.sacolao.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FiscalService {

    private static final Logger log = LoggerFactory.getLogger(FiscalService.class);
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter EMISSION_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final EstablishmentFiscalSettingsRepository settingsRepository;
    private final EstablishmentRepository establishmentRepository;
    private final OrderRepository orderRepository;
    private final FocusNfeClient focusNfeClient;

    public FiscalService(
            EstablishmentFiscalSettingsRepository settingsRepository,
            EstablishmentRepository establishmentRepository,
            OrderRepository orderRepository,
            FocusNfeClient focusNfeClient
    ) {
        this.settingsRepository = settingsRepository;
        this.establishmentRepository = establishmentRepository;
        this.orderRepository = orderRepository;
        this.focusNfeClient = focusNfeClient;
    }

    @Transactional(readOnly = true)
    public FiscalSettingsResponse getSettings() {
        return FiscalMapper.toSettingsResponse(requireSettings(requireTenantEstablishment()));
    }

    @Transactional
    public FiscalSettingsResponse updateSettings(UpdateFiscalSettingsRequest request) {
        Establishment establishment = requireTenantEstablishment();
        EstablishmentFiscalSettings settings = settingsRepository.findById(establishment.getId())
                .orElseGet(() -> {
                    EstablishmentFiscalSettings created = new EstablishmentFiscalSettings();
                    created.setEstablishmentId(establishment.getId());
                    return created;
                });

        settings.setEnabled(Boolean.TRUE.equals(request.enabled()));
        settings.setEnvironment(request.environment() == null ? FiscalEnvironment.HOMOLOG : request.environment());
        if (request.apiToken() != null) {
            String token = request.apiToken().trim();
            if (!token.isBlank()) {
                settings.setApiToken(token);
            }
        }
        if (request.cnpj() != null) {
            String digits = onlyDigits(request.cnpj());
            if (!digits.isBlank() && digits.length() != 14) {
                throw new UnprocessableException("INVALID_CNPJ", "CNPJ deve ter 14 dígitos");
            }
            settings.setCnpj(digits.isBlank() ? null : digits);
        }
        if (request.autoEmitOnPaid() != null) {
            settings.setAutoEmitOnPaid(request.autoEmitOnPaid());
        }
        if (request.defaultNcm() != null && !request.defaultNcm().isBlank()) {
            settings.setDefaultNcm(onlyDigits(request.defaultNcm()));
        }
        if (request.defaultCfop() != null && !request.defaultCfop().isBlank()) {
            settings.setDefaultCfop(onlyDigits(request.defaultCfop()));
        }
        if (request.icmsOrigem() != null) {
            settings.setIcmsOrigem(request.icmsOrigem());
        }
        if (request.icmsSituacaoTributaria() != null && !request.icmsSituacaoTributaria().isBlank()) {
            settings.setIcmsSituacaoTributaria(request.icmsSituacaoTributaria().trim());
        }
        return FiscalMapper.toSettingsResponse(settingsRepository.save(settings));
    }

    @Transactional(readOnly = true)
    public FiscalConnectionTestResponse testConnection() {
        EstablishmentFiscalSettings settings = requireSettings(requireTenantEstablishment());
        if (settings.getApiToken() == null || settings.getApiToken().isBlank()) {
            throw new UnprocessableException("FOCUS_TOKEN_MISSING", "Configure o token da Focus NFe antes de testar");
        }
        try {
            JsonNode node = focusNfeClient.testConnection(settings.getApiToken(), settings.getEnvironment());
            String hint = node.isArray()
                    ? ("Conexão OK · " + node.size() + " empresa(s) na Focus")
                    : "Conexão OK com a Focus NFe";
            return new FiscalConnectionTestResponse(true, hint);
        } catch (UnprocessableException ex) {
            return new FiscalConnectionTestResponse(false, ex.getMessage());
        }
    }

    @Transactional
    public NfceInfoResponse emitForOrder(UUID orderId) {
        Establishment establishment = requireTenantEstablishment();
        EstablishmentFiscalSettings settings = requireSettings(establishment);
        if (!settings.isReadyToEmit()) {
            throw new UnprocessableException(
                    "FISCAL_NOT_READY",
                    "Ative a NFC-e e informe token + CNPJ válidos na aba Fiscal"
            );
        }
        Order order = orderRepository.findDetailedByIdAndEstablishmentId(orderId, establishment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        return emitInternal(order, settings, false);
    }

    @Transactional
    public NfceInfoResponse consultForOrder(UUID orderId) {
        Establishment establishment = requireTenantEstablishment();
        EstablishmentFiscalSettings settings = requireSettings(establishment);
        Order order = orderRepository.findDetailedByIdAndEstablishmentId(orderId, establishment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        if (order.getNfceRef() == null || order.getNfceRef().isBlank()) {
            throw new UnprocessableException("NFCE_REF_MISSING", "Este pedido ainda não tem NFC-e emitida");
        }
        if (settings.getApiToken() == null || settings.getApiToken().isBlank()) {
            throw new UnprocessableException("FOCUS_TOKEN_MISSING", "Token da Focus NFe não configurado");
        }
        JsonNode node = focusNfeClient.consultNfce(
                settings.getApiToken(),
                settings.getEnvironment(),
                order.getNfceRef()
        );
        applyFocusResponse(order, node, settings.getEnvironment());
        return FiscalMapper.toNfceInfo(orderRepository.save(order));
    }

    /** Melhor esforço: nunca propaga falha para o fluxo de pagamento. */
    @Transactional
    public void tryAutoEmitAfterPaid(UUID orderId) {
        try {
            UUID tenantId = TenantContext.get().orElse(null);
            if (tenantId == null) {
                Order probe = orderRepository.findById(orderId).orElse(null);
                if (probe == null) {
                    return;
                }
                tenantId = probe.getEstablishmentId();
            }
            EstablishmentFiscalSettings settings = settingsRepository.findById(tenantId).orElse(null);
            if (settings == null || !settings.isReadyToEmit() || !settings.isAutoEmitOnPaid()) {
                return;
            }
            Order order = orderRepository.findDetailedByIdAndEstablishmentId(orderId, tenantId).orElse(null);
            if (order == null) {
                return;
            }
            if (order.getNfceStatus() == NfceStatus.AUTHORIZED || order.getNfceStatus() == NfceStatus.PROCESSING) {
                return;
            }
            emitInternal(order, settings, true);
        } catch (Exception ex) {
            log.warn("Falha ao auto-emitir NFC-e do pedido {}: {}", orderId, ex.getMessage());
        }
    }

    private NfceInfoResponse emitInternal(Order order, EstablishmentFiscalSettings settings, boolean softFail) {
        if (order.getNfceStatus() == NfceStatus.AUTHORIZED) {
            return FiscalMapper.toNfceInfo(order);
        }
        if (order.getNfceStatus() == NfceStatus.PROCESSING
                && order.getNfceRef() != null
                && !order.getNfceRef().isBlank()) {
            try {
                JsonNode node = focusNfeClient.consultNfce(
                        settings.getApiToken(),
                        settings.getEnvironment(),
                        order.getNfceRef()
                );
                applyFocusResponse(order, node, settings.getEnvironment());
                return FiscalMapper.toNfceInfo(orderRepository.save(order));
            } catch (Exception ex) {
                if (softFail) {
                    log.warn("Consulta NFC-e em processamento falhou: {}", ex.getMessage());
                    return FiscalMapper.toNfceInfo(order);
                }
                throw ex instanceof UnprocessableException upe
                        ? upe
                        : new UnprocessableException("FOCUS_NFE_ERROR", "Não foi possível consultar a NFC-e");
            }
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            failOrThrow(order, softFail, "Pedido sem itens para emitir NFC-e");
            return FiscalMapper.toNfceInfo(order);
        }

        String ref = order.getNfceRef();
        if (ref == null || ref.isBlank()
                || order.getNfceStatus() == NfceStatus.ERROR
                || order.getNfceStatus() == NfceStatus.DENIED
                || order.getNfceStatus() == NfceStatus.NONE) {
            ref = "ord-" + order.getId().toString().replace("-", "");
        }

        order.setNfceRef(ref);
        order.setNfceStatus(NfceStatus.PROCESSING);
        order.setNfceErrorMessage(null);
        orderRepository.save(order);

        try {
            Map<String, Object> body = buildNfcePayload(order, settings);
            JsonNode node = focusNfeClient.emitNfce(
                    settings.getApiToken(),
                    settings.getEnvironment(),
                    ref,
                    body
            );
            applyFocusResponse(order, node, settings.getEnvironment());
            return FiscalMapper.toNfceInfo(orderRepository.save(order));
        } catch (UnprocessableException ex) {
            order.setNfceStatus(NfceStatus.ERROR);
            order.setNfceErrorMessage(truncate(ex.getMessage(), 1000));
            orderRepository.save(order);
            if (softFail) {
                log.warn("Emissão NFC-e recusada para {}: {}", order.getId(), ex.getMessage());
                return FiscalMapper.toNfceInfo(order);
            }
            throw ex;
        } catch (Exception ex) {
            order.setNfceStatus(NfceStatus.ERROR);
            order.setNfceErrorMessage(truncate(ex.getMessage(), 1000));
            orderRepository.save(order);
            if (softFail) {
                log.warn("Emissão NFC-e falhou para {}: {}", order.getId(), ex.getMessage());
                return FiscalMapper.toNfceInfo(order);
            }
            throw new UnprocessableException("FOCUS_NFE_ERROR", "Não foi possível emitir a NFC-e");
        }
    }

    private void failOrThrow(Order order, boolean softFail, String message) {
        order.setNfceStatus(NfceStatus.ERROR);
        order.setNfceErrorMessage(message);
        orderRepository.save(order);
        if (!softFail) {
            throw new UnprocessableException("NFCE_INVALID_ORDER", message);
        }
    }

    private Map<String, Object> buildNfcePayload(Order order, EstablishmentFiscalSettings settings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cnpj_emitente", onlyDigits(settings.getCnpj()));
        body.put("natureza_operacao", "VENDA AO CONSUMIDOR");
        body.put("data_emissao", ZonedDateTime.now(SAO_PAULO).format(EMISSION_FMT));
        body.put("tipo_documento", "1");
        body.put("presenca_comprador", order.getFulfillmentType() == FulfillmentType.DELIVERY ? "4" : "1");
        body.put("local_destino", "1");
        boolean hasFreight = order.getDeliveryFee() != null && order.getDeliveryFee().compareTo(BigDecimal.ZERO) > 0;
        body.put("modalidade_frete", hasFreight ? "0" : "9");
        if (hasFreight) {
            body.put("valor_frete", money(order.getDeliveryFee()));
        }
        if (order.getDiscount() != null && order.getDiscount().compareTo(BigDecimal.ZERO) > 0) {
            body.put("valor_desconto", money(order.getDiscount()));
        }
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank()) {
            body.put("nome_destinatario", order.getCustomerName().trim());
        }

        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (OrderItem item : order.getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("numero_item", String.valueOf(index++));
            row.put("codigo_produto", item.getProduct() != null
                    ? item.getProduct().getId().toString().replace("-", "").substring(0, 16)
                    : ("ITEM" + index));
            row.put("descricao", truncate(item.getProductName(), 120));
            row.put("codigo_ncm", settings.getDefaultNcm());
            row.put("cfop", settings.getDefaultCfop());
            BigDecimal qty = item.getQuantity().setScale(4, RoundingMode.HALF_UP);
            BigDecimal unit = item.getUnitPrice().setScale(4, RoundingMode.HALF_UP);
            BigDecimal bruto = item.getSubtotal().setScale(2, RoundingMode.HALF_UP);
            row.put("quantidade_comercial", qty);
            row.put("quantidade_tributavel", qty);
            row.put("valor_unitario_comercial", unit);
            row.put("valor_unitario_tributavel", unit);
            row.put("valor_bruto", bruto);
            String unitCode = unitCode(item.getProductUnit());
            row.put("unidade_comercial", unitCode);
            row.put("unidade_tributavel", unitCode);
            row.put("icms_origem", String.valueOf(settings.getIcmsOrigem()));
            row.put("icms_situacao_tributaria", settings.getIcmsSituacaoTributaria());
            items.add(row);
        }
        body.put("items", items);

        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("forma_pagamento", focusPaymentCode(order.getPaymentMethod()));
        payment.put("valor_pagamento", money(order.getTotal()));
        body.put("formas_pagamento", List.of(payment));
        return body;
    }

    private void applyFocusResponse(Order order, JsonNode node, FiscalEnvironment environment) {
        String status = text(node, "status");
        order.setNfceStatus(mapStatus(status));
        order.setNfceNumber(text(node, "numero"));
        order.setNfceSeries(text(node, "serie"));
        order.setNfceChave(text(node, "chave_nfe"));
        order.setNfceQrcodeUrl(text(node, "qrcode_url"));
        order.setNfceUrlDanfe(absoluteFocusUrl(environment, text(node, "caminho_danfe")));
        order.setNfceUrlXml(absoluteFocusUrl(environment, firstNonBlank(
                text(node, "caminho_xml_nota_fiscal"),
                text(node, "caminho_xml")
        )));
        String mensagem = firstNonBlank(
                text(node, "mensagem_sefaz"),
                text(node, "mensagem"),
                text(node, "message")
        );
        if (order.getNfceStatus() == NfceStatus.AUTHORIZED) {
            order.setNfceErrorMessage(null);
            order.setNfceEmittedAt(Instant.now());
        } else if (mensagem != null) {
            order.setNfceErrorMessage(truncate(mensagem, 1000));
        }
    }

    private static NfceStatus mapStatus(String status) {
        if (status == null || status.isBlank()) {
            return NfceStatus.PROCESSING;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "autorizado" -> NfceStatus.AUTHORIZED;
            case "cancelado" -> NfceStatus.CANCELLED;
            case "erro_autorizacao", "denegado" -> NfceStatus.DENIED;
            case "processando_autorizacao", "processando" -> NfceStatus.PROCESSING;
            default -> NfceStatus.ERROR;
        };
    }

    private static String absoluteFocusUrl(FiscalEnvironment environment, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String base = environment == FiscalEnvironment.PRODUCTION
                ? "https://api.focusnfe.com.br"
                : "https://homologacao.focusnfe.com.br";
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private static String focusPaymentCode(PaymentMethod method) {
        if (method == null) {
            return "99";
        }
        return switch (method) {
            case PIX -> "17";
            case CASH -> "01";
            case CARD -> "03";
            case ON_DELIVERY -> "01";
        };
    }

    private static String unitCode(ProductUnit unit) {
        if (unit == null) {
            return "UN";
        }
        return switch (unit) {
            case KG -> "KG";
            case G -> "G";
            case L -> "L";
            case ML -> "ML";
            case CX -> "CX";
            case PCT -> "PCT";
            case UN -> "UN";
        };
    }

    private EstablishmentFiscalSettings requireSettings(Establishment establishment) {
        return settingsRepository.findById(establishment.getId()).orElseGet(() -> {
            EstablishmentFiscalSettings created = new EstablishmentFiscalSettings();
            created.setEstablishmentId(establishment.getId());
            return settingsRepository.save(created);
        });
    }

    private Establishment requireTenantEstablishment() {
        return establishmentRepository.findById(TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String onlyDigits(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.get(field).asString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
