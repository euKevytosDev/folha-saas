package com.sacolao.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal quantity(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }
}
