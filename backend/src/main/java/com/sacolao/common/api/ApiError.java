package com.sacolao.common.api;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldErrorDetail> errors
) {
    public static ApiError of(int status, String code, String message, String path, List<FieldErrorDetail> errors) {
        return new ApiError(Instant.now(), status, code, message, path, errors == null ? List.of() : errors);
    }
}
