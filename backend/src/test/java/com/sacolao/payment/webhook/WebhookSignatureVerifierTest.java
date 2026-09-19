package com.sacolao.payment.webhook;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {

    @Test
    void validatesMercadoPagoSignature() throws Exception {
        String secret = "test-secret";
        String dataId = "12345";
        String requestId = "req-1";
        String ts = "1704908010";
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        String signature = "ts=" + ts + ",v1=" + hash;

        assertTrue(WebhookSignatureVerifier.isValidMercadoPago(secret, dataId, requestId, signature));
        assertFalse(WebhookSignatureVerifier.isValidMercadoPago(secret, dataId, requestId, "ts=" + ts + ",v1=deadbeef"));
    }

    @Test
    void matchesSharedSecret() {
        assertTrue(WebhookSignatureVerifier.matchesSharedSecret("abc", "abc"));
        assertFalse(WebhookSignatureVerifier.matchesSharedSecret("abc", "xyz"));
    }
}
