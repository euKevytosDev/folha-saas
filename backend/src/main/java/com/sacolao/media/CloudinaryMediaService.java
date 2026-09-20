package com.sacolao.media;

import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.config.AppProperties;
import com.sacolao.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class CloudinaryMediaService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryMediaService.class);

    static final String INCOMING_TRANSFORMATION = "c_limit,w_1600,h_1600/q_auto:good";

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final AppProperties properties;
    private final RestClient.Builder restClientBuilder;

    public CloudinaryMediaService(AppProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    @PostConstruct
    void logStatus() {
        Credentials credentials = credentials();
        if (credentials.enabled()) {
            log.info("Cloudinary habilitado cloud={} folder={}", credentials.cloudName(), credentials.folder());
        } else {
            log.warn("Cloudinary não configurado. Defina CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY e CLOUDINARY_API_SECRET, ou CLOUDINARY_URL.");
        }
    }

    public boolean isEnabled() {
        return credentials().enabled();
    }

    public long maxBytes() {
        var cloudinary = properties.cloudinary();
        return cloudinary == null || cloudinary.maxBytes() <= 0
                ? 5L * 1024 * 1024
                : cloudinary.maxBytes();
    }

    public MediaUploadResponse upload(MultipartFile file) {
        Credentials credentials = credentials();
        if (!credentials.enabled()) {
            throw new UnprocessableException("MEDIA_DISABLED", "Upload de imagens não configurado neste ambiente");
        }
        if (file == null || file.isEmpty()) {
            throw new UnprocessableException("MEDIA_EMPTY", "Selecione uma imagem");
        }
        if (file.getSize() > maxBytes()) {
            throw new UnprocessableException("MEDIA_TOO_LARGE", "Imagem deve ter no máximo " + maxMbLabel());
        }
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new UnprocessableException("MEDIA_TYPE", "Use JPG, PNG, WEBP ou GIF");
        }

        UUID tenantId = TenantContext.get().orElse(null);
        String folder = buildFolder(credentials.folder(), tenantId);
        long timestamp = Instant.now().getEpochSecond();
        String signature = sign(Map.of(
                "folder", folder,
                "timestamp", String.valueOf(timestamp),
                "transformation", INCOMING_TRANSFORMATION
        ), credentials.apiSecret());

        try {
            byte[] bytes = file.getBytes();
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    String name = file.getOriginalFilename();
                    return StringUtils.hasText(name) ? name : "upload.jpg";
                }
            }).contentType(MediaType.parseMediaType(contentType));
            body.part("api_key", credentials.apiKey());
            body.part("timestamp", String.valueOf(timestamp));
            body.part("folder", folder);
            body.part("transformation", INCOMING_TRANSFORMATION);
            body.part("signature", signature);

            String endpoint = "https://api.cloudinary.com/v1_1/"
                    + credentials.cloudName()
                    + "/image/upload";

            JsonNode response = restClientBuilder.build()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new UnprocessableException("MEDIA_UPLOAD_FAILED", "Falha ao enviar imagem");
            }
            String url = text(response, "secure_url");
            if (url == null || url.isBlank()) {
                url = text(response, "url");
            }
            if (url == null || url.isBlank()) {
                throw new UnprocessableException("MEDIA_UPLOAD_FAILED", "Cloudinary não retornou URL");
            }
            return new MediaUploadResponse(
                    url,
                    text(response, "public_id"),
                    text(response, "format"),
                    intValue(response, "width"),
                    intValue(response, "height"),
                    intValue(response, "bytes")
            );
        } catch (UnprocessableException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Falha no upload Cloudinary status={} body={}", ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString()));
            throw new UnprocessableException("MEDIA_UPLOAD_FAILED", "Falha ao enviar imagem ao Cloudinary");
        } catch (Exception ex) {
            log.warn("Falha no upload Cloudinary: {}", ex.toString());
            throw new UnprocessableException("MEDIA_UPLOAD_FAILED", "Falha ao enviar imagem");
        }
    }

    Credentials credentials() {
        var cloudinary = properties.cloudinary();
        String cloudName = clean(cloudinary == null ? null : cloudinary.cloudName());
        String apiKey = clean(cloudinary == null ? null : cloudinary.apiKey());
        String apiSecret = clean(cloudinary == null ? null : cloudinary.apiSecret());
        ParsedUrl parsed = parseCloudinaryUrl(cloudinary == null ? null : cloudinary.url());
        if (parsed != null) {
            if (!StringUtils.hasText(cloudName)) {
                cloudName = parsed.cloudName();
            }
            if (!StringUtils.hasText(apiKey)) {
                apiKey = parsed.apiKey();
            }
            if (!StringUtils.hasText(apiSecret)) {
                apiSecret = parsed.apiSecret();
            }
        }
        String folder = cloudinary == null ? "folha" : clean(cloudinary.folder());
        if (!StringUtils.hasText(folder)) {
            folder = "folha";
        }
        return new Credentials(cloudName, apiKey, apiSecret, folder);
    }

    private String maxMbLabel() {
        long mb = Math.max(1, Math.round(maxBytes() / (1024.0 * 1024.0)));
        return mb + " MB";
    }

    private static String buildFolder(String configured, UUID tenantId) {
        String base = StringUtils.hasText(configured) ? configured.trim().replaceAll("^/+|/+$", "") : "folha";
        if (tenantId != null) {
            return base + "/" + tenantId;
        }
        return base;
    }

    static String sign(Map<String, String> params, String apiSecret) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder payload = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!payload.isEmpty()) {
                payload.append('&');
            }
            payload.append(entry.getKey()).append('=').append(entry.getValue());
        }
        payload.append(apiSecret);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao assinar upload", ex);
        }
    }

    static ParsedUrl parseCloudinaryUrl(String raw) {
        String value = clean(raw);
        if (value == null) {
            return null;
        }
        String prefix = "cloudinary://";
        if (!value.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String rest = value.substring(prefix.length());
        int at = rest.lastIndexOf('@');
        if (at <= 0 || at == rest.length() - 1) {
            return null;
        }
        String userInfo = rest.substring(0, at);
        String host = rest.substring(at + 1);
        int colon = userInfo.indexOf(':');
        if (colon <= 0 || colon == userInfo.length() - 1) {
            return null;
        }
        String apiKey = userInfo.substring(0, colon);
        String apiSecret = userInfo.substring(colon + 1);
        String cloudName = host.split("/")[0];
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret) || !StringUtils.hasText(cloudName)) {
            return null;
        }
        return new ParsedUrl(cloudName, apiKey, apiSecret);
    }

    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeContentType(String contentType, String filename) {
        if (StringUtils.hasText(contentType)) {
            String type = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if ("image/jpg".equals(type)) {
                return "image/jpeg";
            }
            return type;
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text;
    }

    private static Integer intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode() || !value.isNumber()) {
            return null;
        }
        return value.intValue();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }

    record Credentials(String cloudName, String apiKey, String apiSecret, String folder) {
        boolean enabled() {
            return StringUtils.hasText(cloudName)
                    && StringUtils.hasText(apiKey)
                    && StringUtils.hasText(apiSecret);
        }
    }

    record ParsedUrl(String cloudName, String apiKey, String apiSecret) {
    }
}
