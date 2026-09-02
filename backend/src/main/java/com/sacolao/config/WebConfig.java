package com.sacolao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final AppProperties properties;

    public WebConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = resolveFrontendLocation();
        log.info("Frontend estático servido a partir de {}", location);

        registry.addResourceHandler("/css/**")
                .addResourceLocations(location + "css/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
        registry.addResourceHandler("/js/**")
                .addResourceLocations(location + "js/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "assets/")
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
        registry.addResourceHandler("/pages/**")
                .addResourceLocations(location + "pages/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/public/**")
                .addResourceLocations(location + "public/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());
        registry.addResourceHandler("/favicon.svg", "/robots.txt")
                .addResourceLocations(location + "public/")
                .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
        registry.setOrder(Ordered.LOWEST_PRECEDENCE);
    }

    private String resolveFrontendLocation() {
        String configured = properties.frontendDir();
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                return path.toUri().toString();
            }
        }

        Path sibling = Path.of(System.getProperty("user.dir"), "..", "frontend").normalize();
        if (Files.isDirectory(sibling.resolve("css"))) {
            return sibling.toUri().toString();
        }

        Path workspace = Path.of(System.getProperty("user.dir"), "frontend").normalize();
        if (Files.isDirectory(workspace.resolve("css"))) {
            return workspace.toUri().toString();
        }

        return "classpath:/static/";
    }
}
