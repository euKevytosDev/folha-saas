package com.sacolao.mail;

import com.sacolao.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class ResendEmailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailClient.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final AppProperties properties;
    private final JsonMapper jsonMapper;

    public ResendEmailClient(AppProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public boolean isConfigured() {
        AppProperties.Resend resend = properties.resend();
        return resend != null
                && resend.apiKey() != null
                && !resend.apiKey().isBlank()
                && resend.from() != null
                && !resend.from().isBlank();
    }

    public void send(String to, String subject, String html, String text) {
        if (!isConfigured()) {
            throw new IllegalStateException("Resend não configurado");
        }
        AppProperties.Resend resend = properties.resend();
        try {
            ObjectNode body = jsonMapper.createObjectNode();
            body.put("from", resend.from().trim());
            ArrayNode toNode = body.putArray("to");
            toNode.add(to.trim());
            body.put("subject", subject);
            if (html != null && !html.isBlank()) {
                body.put("html", html);
            }
            if (text != null && !text.isBlank()) {
                body.put("text", text);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + resend.apiKey().trim())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "folha-saas/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("Resend HTTP {} body={}", response.statusCode(), truncate(response.body()));
                throw new IllegalStateException("Falha ao enviar e-mail");
            }
            log.info("E-mail enviado via Resend para {}", mask(to));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Erro ao chamar Resend", ex);
            throw new IllegalStateException("Falha ao enviar e-mail", ex);
        }
    }

    public void sendPasswordReset(String to, String resetUrl) {
        String subject = "Recuperação de senha — Folha";
        String text = "Use o link abaixo para redefinir sua senha (válido por tempo limitado):\n\n" + resetUrl
                + "\n\nSe você não pediu isso, ignore este e-mail.";
        String html = """
                <p>Olá,</p>
                <p>Recebemos um pedido para redefinir a senha da sua conta no <strong>Folha</strong>.</p>
                <p><a href="%s">Clique aqui para criar uma nova senha</a></p>
                <p>Se o botão não funcionar, copie e cole este link no navegador:</p>
                <p>%s</p>
                <p>Se você não solicitou, pode ignorar este e-mail.</p>
                """.formatted(resetUrl, resetUrl);
        send(to, subject, html, text);
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }
}
