package com.sacolao.coupon.dto;

import com.sacolao.coupon.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateCouponRequest(
        @Size(max = 255) String description,
        DiscountType discountType,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal discountValue,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal minOrderAmount,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal maxDiscountAmount,
        @Min(1) Integer usageLimit,
        Boolean active,
        Instant startsAt,
        Instant endsAt
) {
}
