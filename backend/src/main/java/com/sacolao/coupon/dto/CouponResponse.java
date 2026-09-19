package com.sacolao.coupon.dto;

import com.sacolao.coupon.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Integer usageLimit,
        int usedCount,
        boolean active,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt
) {
}
