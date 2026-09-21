package com.sacolao.fiscal;

import com.sacolao.catalog.CatalogSupport;
import com.sacolao.fiscal.focus.FocusNfeClient;
import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FiscalIT extends CatalogSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @MockitoBean
    private FocusNfeClient focusNfeClient;

    @Test
    void ownerCanUpdateFiscalSettings() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Fiscal", "Fis", AuthApi.uniqueEmail("fisc"), "senha12345");
        String token = AuthApi.accessToken(registered);

        mockMvc.perform(get("/api/v1/fiscal/settings")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.tokenConfigured").value(false));

        mockMvc.perform(put("/api/v1/fiscal/settings")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled":true,
                                  "environment":"HOMOLOG",
                                  "apiToken":"focus-test-token",
                                  "cnpj":"12.345.678/0001-95",
                                  "autoEmitOnPaid":true,
                                  "defaultNcm":"21069090",
                                  "defaultCfop":"5102",
                                  "icmsOrigem":0,
                                  "icmsSituacaoTributaria":"102"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.tokenConfigured").value(true))
                .andExpect(jsonPath("$.cnpj").value("12345678000195"));
    }

    @Test
    void emitNfceOnPaidOrder() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja NFC-e", "Nfe", AuthApi.uniqueEmail("nfce"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Hortifruti");
        String productId = createProduct(token, categoryId, "Banana", "5.00", "KG");

        mockMvc.perform(put("/api/v1/fiscal/settings")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled":true,
                                  "environment":"HOMOLOG",
                                  "apiToken":"focus-test-token",
                                  "cnpj":"11222333000181",
                                  "autoEmitOnPaid":false,
                                  "defaultNcm":"21069090",
                                  "defaultCfop":"5102",
                                  "icmsOrigem":0,
                                  "icmsSituacaoTributaria":"102"
                                }
                                """))
                .andExpect(status().isOk());

        String orderId = AuthApi.read(mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente NFC",
                                  "customerPhone":"11955556666",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"CASH"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/payment/confirm")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        ObjectNode authorized = JSON.createObjectNode();
        authorized.put("status", "autorizado");
        authorized.put("numero", "10");
        authorized.put("serie", "1");
        authorized.put("chave_nfe", "NFe35123456789012345678901234567890123456789012");
        authorized.put("caminho_danfe", "/arquivos/danfe.html");
        authorized.put("qrcode_url", "https://example.com/qrcode");
        when(focusNfeClient.emitNfce(anyString(), any(), anyString(), anyMap())).thenReturn(authorized);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/nfce")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.number").value("10"))
                .andExpect(jsonPath("$.danfeUrl").value("https://homologacao.focusnfe.com.br/arquivos/danfe.html"));

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nfce.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.nfce.number").value("10"));
    }
}
