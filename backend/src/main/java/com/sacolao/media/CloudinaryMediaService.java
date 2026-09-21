package com.sacolao.media;

import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.config.AppProperties;
import com.sacolao.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
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
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Incoming transform — mantém proporção e limita o lado maior. */
    static final String INCOMING_TRANSFORMATION = "c_limit,w_1600,h_1600/q_auto:good";

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private final AppProperties properties;

    public CloudinaryMediaService(AppProperties properties) {
        this.properties = properties;
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
        try {
            return doUpload(file);
        } catch (UnprocessableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Falha inesperada no upload de mídia", ex);
            throw new UnprocessableException(
                    "MEDIA_UPLOAD_FAILED",
                    "Falha ao enviar imagem. Tente JPG/PNG menor ou cole a URL."
            );
        }
    }

    private MediaUploadResponse doUpload(MultipartFile file) throws IOException, InterruptedException {
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

        byte[] bytes = file.getBytes();
        String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload.jpg";
        String boundary = "folha-" + UUID.randomUUID().toString().replace("-", "");

        byte[] multipart = buildMultipart(
                boundary,
                Map.of(
                        "api_key", credentials.apiKey(),
                        "timestamp", String.valueOf(timestamp),
                        "folder", folder,
                        "transformation", INCOMING_TRANSFORMATION,
                        "signature", signature
                ),
                filename,
                contentType,
                bytes
        );

        String endpoint = "https://api.cloudinary.com/v1_1/"
                + credentials.cloudName()
                + "/image/upload";

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String raw = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Cloudinary HTTP {} body={}", response.statusCode(), truncate(raw));
            String detail = extractCloudinaryError(raw);
            throw new UnprocessableException(
                    "MEDIA_UPLOAD_FAILED",
                    detail != null ? detail : ("Cloudinary recusou o upload (" + response.statusCode() + ")")
            );
        }

        JsonNode json;
        try {
            json = JSON_MAPPER.readTree(raw);
        } catch (Exception parseEx) {
            log.warn("Resposta Cloudinary inválida: {}", truncate(raw));
            throw new UnprocessableException("MEDIA_UPLOAD_FAILED", "Resposta inválida do Cloudinary");
        }

        String url = text(json, "secure_url");
        if (url == null || url.isBlank()) {
            url = text(json, "url");
        }
        if (url == null || url.isBlank()) {
            String cloudError = extractCloudinaryError(raw);
            throw new UnprocessableException(
                    "MEDIA_UPLOAD_FAILED",
                    cloudError != null ? cloudError : "Cloudinary não retornou URL"
            );
        }
        return new MediaUploadResponse(
                url,
                text(json, "public_id"),
                text(json, "format"),
                intValue(json, "width"),
                intValue(json, "height"),
                intValue(json, "bytes")
        );
    }

    private static byte[] buildMultipart(
            String boundary,
            Map<String, String> fields,
            String filename,
            String contentType,
            byte[] fileBytes
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String dash = "--" + boundary;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            out.write((dash + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write((dash + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + sanitizeFilename(filename) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write((dash + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String sanitizeFilename(String filename) {
        String cleaned = filename.replace("\"", "").replace("\r", "").replace("\n", "");
        return StringUtils.hasText(cleaned) ? cleaned : "upload.jpg";
    }

    private String extractCloudinaryError(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = JSON_MAPPER.readTree(raw);
            JsonNode error = root.get("error");
            if (error == null || error.isNull()) {
                return null;
            }
            if (error.isValueNode()) {
                return error.asString();
            }
            return text(error, "message");
        } catch (Exception ignored) {
            return null;
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
