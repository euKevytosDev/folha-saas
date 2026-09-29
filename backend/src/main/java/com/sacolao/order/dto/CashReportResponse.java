package com.sacolao.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashReportResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal receivedAmount,
        long receivedCount,
        BigDecimal receivableAmount,
        long receivableCount,
        BigDecimal undeliveredAmount,
        long undeliveredCount,
        BigDecimal cancelledAmount,
        long cancelledCount,
        List<CashDayPoint> days
) {
}
