package com.sacolao.media;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudinaryMediaServiceTest {

    @Test
    void signsParamsAlphabeticallyWithSha1() {
        // Example style from Cloudinary docs: sorted key=value joined by & + secret
        String signature = CloudinaryMediaService.sign(
                Map.of(
                        "timestamp", "1315060510",
                        "folder", "folha"
                ),
                "sample"
        );
        assertEquals(40, signature.length());
        assertTrue(signature.matches("[0-9a-f]{40}"));
    }

    @Test
    void signatureChangesWithSecret() {
        Map<String, String> params = Map.of("folder", "folha", "timestamp", "1");
        assertFalse(CloudinaryMediaService.sign(params, "a")
                .equals(CloudinaryMediaService.sign(params, "b")));
    }

    @Test
    void parsesCloudinaryUrl() {
        var parsed = CloudinaryMediaService.parseCloudinaryUrl(
                " cloudinary://123456789012345:abcdefghijklmnopqrstuvwxyzAB@demo-cloud "
        );
        assertEquals("demo-cloud", parsed.cloudName());
        assertEquals("123456789012345", parsed.apiKey());
        assertEquals("abcdefghijklmnopqrstuvwxyzAB", parsed.apiSecret());
    }

    @Test
    void stripsQuotesFromCloudinaryUrl() {
        var parsed = CloudinaryMediaService.parseCloudinaryUrl(
                "\"cloudinary://key:secret@mycloud\""
        );
        assertEquals("mycloud", parsed.cloudName());
        assertEquals("key", parsed.apiKey());
        assertEquals("secret", parsed.apiSecret());
    }

    @Test
    void incomingTransformationKeepsAspectRatio() {
        assertTrue(CloudinaryMediaService.INCOMING_TRANSFORMATION.contains("c_limit"));
        assertTrue(CloudinaryMediaService.INCOMING_TRANSFORMATION.contains("w_1600"));
    }
}
