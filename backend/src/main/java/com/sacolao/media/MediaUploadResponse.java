package com.sacolao.media;

public record MediaUploadResponse(
        String url,
        String publicId,
        String format,
        Integer width,
        Integer height,
        Integer bytes
) {
}
