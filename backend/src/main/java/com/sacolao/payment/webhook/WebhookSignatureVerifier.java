package com.sacolao.payment.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

public final class WebhookSignatureVerifier {

    private WebhookSignatureVerifier() {
    }

    /**
     * Valida assinatura no estilo Mercado Pago (x-signature + x-request-id).
     * Manifest: id:{dataId};request-id:{requestId};ts:{ts};
     */
    public static boolean isValidMercadoPago(
            String secret,
            String dataId,
            String requestId,
            String xSignature
    ) {
        if (secret == null || secret.isBlank() || xSignature == null || xSignature.isBlank()) {
            return false;
        }
        Map<String, String> parts = parseSignature(xSignature);
        String ts = parts.get("ts");
        String hash = parts.get("v1");
        if (ts == null || hash == null || dataId == null || dataId.isBlank()) {
            return false;
        }
        String requestPart = requestId == null ? "" : requestId;
        String manifest = "id:" + dataId + ";request-id:" + requestPart + ";ts:" + ts + ";";
        String expected = hmacSha256Hex(secret, manifest);
        return constantTimeEquals(expected, hash.toLowerCase(Locale.ROOT));
    }

    public static boolean matchesSharedSecret(String configured, String provided) {
        if (configured == null || configured.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return constantTimeEquals(configured.trim(), provided.trim());
    }

    private static Map<String, String> parseSignature(String signature) {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        for (String part : signature.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim().toLowerCase(Locale.ROOT), kv[1].trim());
            }
        }
        return map;
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Falha ao calcular HMAC", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
