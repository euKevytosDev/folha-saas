package com.sacolao.store.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartQuoteResponse(
        List<CartQuoteLineResponse> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal deliveryFee,
        BigDecimal total
) {
}
