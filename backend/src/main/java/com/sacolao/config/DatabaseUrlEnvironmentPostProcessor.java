package com.sacolao.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Aceita DATABASE_URL no formato postgres:// / postgresql:// (Render/Heroku)
 * e converte para jdbc:postgresql:// + username/password.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = firstNonBlank(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("SPRING_DATASOURCE_URL")
        );
        if (raw == null || raw.startsWith("jdbc:")) {
            return;
        }
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) {
            return;
        }

        try {
            URI uri = new URI(raw.replaceFirst("^postgres(ql)?://", "http://"));
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                return;
            }
            String[] parts = userInfo.split(":", 2);
            String username = decode(parts[0]);
            String password = parts.length > 1 ? decode(parts[1]) : "";
            String path = uri.getPath() == null ? "" : uri.getPath();
            String database = path.startsWith("/") ? path.substring(1) : path;
            if (database.contains("?")) {
                database = database.substring(0, database.indexOf('?'));
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + database;
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                jdbc = jdbc + "?" + uri.getQuery();
            }

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbc);
            props.put("DATABASE_URL", jdbc);
            props.put("spring.datasource.username", username);
            props.put("DATABASE_USERNAME", username);
            props.put("spring.datasource.password", password);
            props.put("DATABASE_PASSWORD", password);
            environment.getPropertySources().addFirst(new MapPropertySource("renderDatabaseUrl", props));
        } catch (URISyntaxException ignored) {
            // mantém o valor original; o boot falha com mensagem clara se inválido
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
