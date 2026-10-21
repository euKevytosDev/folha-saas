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

@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public boolean sendMessage(String botToken, String chatId, String text) {
        if (blank(botToken) || blank(chatId) || blank(text)) {
            return false;
        }
        try {
            String url = "https://api.telegram.org/bot" + botToken.trim() + "/sendMessage"
                    + "?chat_id=" + URLEncoder.encode(chatId.trim(), StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&disable_web_page_preview=true";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "maxpedidos/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean ok = response.statusCode() < 400 && (response.body() == null || response.body().contains("\"ok\":true"));
            if (!ok) {
                log.warn("Telegram HTTP {} body={}", response.statusCode(), truncate(response.body()));
            }
            return ok;
        } catch (Exception ex) {
            log.warn("Telegram falhou: {}", ex.toString());
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 240 ? body.substring(0, 240) : body;
    }
}
