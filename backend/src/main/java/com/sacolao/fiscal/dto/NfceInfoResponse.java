package com.sacolao.fiscal.dto;

import com.sacolao.fiscal.entity.NfceStatus;

import java.time.Instant;

public record NfceInfoResponse(
        String ref,
        NfceStatus status,
        String number,
        String series,
        String chave,
        String danfeUrl,
        String xmlUrl,
        String qrcodeUrl,
        String errorMessage,
        Instant emittedAt
) {
}
