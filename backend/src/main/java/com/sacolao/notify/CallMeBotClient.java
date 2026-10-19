package com.sacolao.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Envia WhatsApp via TextMeBot (api.textmebot.com).
 * Mantém o nome da classe para não quebrar o wiring existente.
 */
@Component
public class CallMeBotClient {

    private static final Logger log = LoggerFactory.getLogger(CallMeBotClient.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public void sendWhatsApp(String phoneDigits, String apiKey, String text) {
        if (phoneDigits == null || phoneDigits.isBlank() || apiKey == null || apiKey.isBlank()
                || text == null || text.isBlank()) {
            return;
        }
        String digits = phoneDigits.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return;
        }
        String recipient = digits.startsWith("+") ? digits : "+" + digits;
        try {
            String url = "https://api.textmebot.com/send.php"
                    + "?recipient=" + URLEncoder.encode(recipient, StandardCharsets.UTF_8)
                    + "&apikey=" + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&json=yes";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "maxpedidos/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400 || (response.body() != null && response.body().toLowerCase().contains("\"status\":\"error\""))) {
                log.warn("TextMeBot HTTP {} body={}", response.statusCode(), truncate(response.body()));
            } else {
                log.info("TextMeBot ok phoneEnding={}", digits.length() >= 4 ? digits.substring(digits.length() - 4) : "****");
            }
        } catch (Exception ex) {
            log.warn("TextMeBot falhou: {}", ex.toString());
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 240 ? body.substring(0, 240) : body;
    }
}
