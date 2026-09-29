package com.sacolao.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashDayPoint(
        LocalDate date,
        BigDecimal receivedAmount,
        BigDecimal cancelledAmount
) {
}
