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
}
