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
        String phone = phoneDigits.replaceAll("\\D+", "");
        if (phone.isBlank()) {
            return;
        }
        try {
            String url = "https://api.callmebot.com/whatsapp.php"
                    + "?phone=" + URLEncoder.encode(phone, StandardCharsets.UTF_8)
                    + "&apikey=" + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "maxpedidos/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("CallMeBot HTTP {} body={}", response.statusCode(), truncate(response.body()));
            }
        } catch (Exception ex) {
            log.warn("CallMeBot falhou: {}", ex.toString());
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }
}
