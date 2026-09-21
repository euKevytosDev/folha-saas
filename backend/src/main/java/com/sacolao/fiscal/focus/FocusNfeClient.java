package com.sacolao.fiscal.focus;

import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.fiscal.entity.FiscalEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Component
public class FocusNfeClient {

    private static final Logger log = LoggerFactory.getLogger(FocusNfeClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public JsonNode emitNfce(String token, FiscalEnvironment environment, String ref, Map<String, Object> body) {
        String encodedRef = URLEncoder.encode(ref, StandardCharsets.UTF_8);
        String url = baseUrl(environment) + "/v2/nfce?ref=" + encodedRef;
        return exchange("POST", url, token, body);
    }

    public JsonNode consultNfce(String token, FiscalEnvironment environment, String ref) {
        String encodedRef = URLEncoder.encode(ref, StandardCharsets.UTF_8);
        String url = baseUrl(environment) + "/v2/nfce/" + encodedRef;
        return exchange("GET", url, token, null);
    }

    /** Valida o token consultando a empresa na Focus. */
    public JsonNode testConnection(String token, FiscalEnvironment environment) {
        String url = baseUrl(environment) + "/v2/empresas";
        return exchange("GET", url, token, null);
    }

    private JsonNode exchange(String method, String url, String token, Map<String, Object> body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .header("Authorization", basicAuth(token));
            if ("POST".equals(method)) {
                String json = JSON.writeValueAsString(body == null ? Map.of() : body);
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            } else {
                builder.GET();
            }
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String raw = response.body() == null ? "" : response.body();
            JsonNode node;
            try {
                node = raw.isBlank() ? JSON.createObjectNode() : JSON.readTree(raw);
            } catch (Exception parseEx) {
                log.warn("Focus NFe resposta não-JSON status={} body={}", response.statusCode(), truncate(raw));
                throw new UnprocessableException("FOCUS_NFE_ERROR", "Resposta inválida da Focus NFe");
            }
            if (response.statusCode() >= 400) {
                String message = firstMessage(node);
                log.warn("Focus NFe HTTP {} url={} msg={}", response.statusCode(), url, message);
                throw new UnprocessableException(
                        "FOCUS_NFE_ERROR",
                        message != null ? message : ("Focus NFe recusou a operação (" + response.statusCode() + ")")
                );
            }
            return node;
        } catch (UnprocessableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Falha ao chamar Focus NFe", ex);
            throw new UnprocessableException("FOCUS_NFE_ERROR", "Não foi possível falar com a Focus NFe");
        }
    }

    private static String basicAuth(String token) {
        String raw = (token == null ? "" : token.trim()) + ":";
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String baseUrl(FiscalEnvironment environment) {
        return environment == FiscalEnvironment.PRODUCTION
                ? "https://api.focusnfe.com.br"
                : "https://homologacao.focusnfe.com.br";
    }

    private static String firstMessage(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.hasNonNull("mensagem")) {
            return node.get("mensagem").asString();
        }
        if (node.hasNonNull("message")) {
            return node.get("message").asString();
        }
        if (node.has("erros") && node.get("erros").isArray() && !node.get("erros").isEmpty()) {
            JsonNode first = node.get("erros").get(0);
            if (first.hasNonNull("mensagem")) {
                return first.get("mensagem").asString();
            }
            return first.toString();
        }
        return null;
    }

    private static String truncate(String body) {
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }
}
