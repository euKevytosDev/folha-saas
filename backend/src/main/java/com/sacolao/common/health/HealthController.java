package com.sacolao.common.health;

import com.sacolao.config.AppProperties;
import com.sacolao.mail.ResendEmailClient;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final ResendEmailClient resendEmailClient;
    private final AppProperties properties;

    public HealthController(ResendEmailClient resendEmailClient, AppProperties properties) {
        this.resendEmailClient = resendEmailClient;
        this.properties = properties;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", "folha");
        body.put("timestamp", Instant.now());
        body.put("emailConfigured", resendEmailClient.isConfigured());
        body.put("emailTestMode", resendEmailClient.isTestSender());
        String frontend = properties.publicFrontendUrl();
        body.put("publicFrontendConfigured", StringUtils.hasText(frontend)
                && !frontend.contains("localhost"));
        return body;
    }
}
