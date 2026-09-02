package com.sacolao.common.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugify {

    private Slugify() {
    }

    public static String from(String value) {
        if (value == null || value.isBlank()) {
            return "loja";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (slug.isBlank()) {
            return "loja";
        }
        if (slug.length() > 100) {
            slug = slug.substring(0, 100).replaceAll("-+$", "");
        }
        return slug.isBlank() ? "loja" : slug;
    }
}
