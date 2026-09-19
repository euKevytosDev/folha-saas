package com.sacolao.media;

import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.config.AppProperties;
import com.sacolao.tenant.TenantContext;
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

    public boolean isEnabled() {
        var cloudinary = properties.cloudinary();
        return cloudinary != null
                && StringUtils.hasText(cloudinary.cloudName())
                && StringUtils.hasText(cloudinary.apiKey())
                && StringUtils.hasText(cloudinary.apiSecret());
    }

    public long maxBytes() {
        var cloudinary = properties.cloudinary();
        return cloudinary == null || cloudinary.maxBytes() <= 0
                ? 5L * 1024 * 1024
                : cloudinary.maxBytes();
    }

    public MediaUploadResponse upload(MultipartFile file) {
        if (!isEnabled()) {
            throw new UnprocessableException("MEDIA_DISABLED", "Upload de imagens não configurado neste ambiente");
        }
        if (file == null || file.isEmpty()) {
            throw new UnprocessableException("MEDIA_EMPTY", "Selecione uma imagem");
        }
        var cloudinary = properties.cloudinary();
        if (file.getSize() > maxBytes()) {
            throw new UnprocessableException("MEDIA_TOO_LARGE", "Imagem deve ter no máximo 5 MB");
        }
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new UnprocessableException("MEDIA_TYPE", "Use JPG, PNG, WEBP ou GIF");
        }

        UUID tenantId = TenantContext.get().orElse(null);
        String folder = buildFolder(cloudinary.folder(), tenantId);
        long timestamp = Instant.now().getEpochSecond();
        String signature = sign(Map.of(
                "folder", folder,
                "timestamp", String.valueOf(timestamp)
        ), cloudinary.apiSecret());

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
            body.part("api_key", cloudinary.apiKey());
            body.part("timestamp", String.valueOf(timestamp));
            body.part("folder", folder);
            body.part("signature", signature);

            String endpoint = "https://api.cloudinary.com/v1_1/"
                    + cloudinary.cloudName().trim()
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
            throw new UnprocessableException("MEDIA_UPLOAD_FAILED", "Falha ao enviar imagem ao Cloudinary");
        } catch (Exception ex) {
            throw new UnprocessableException("MEDIA_UPLOAD_FAILED", "Falha ao enviar imagem");
        }
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
}
