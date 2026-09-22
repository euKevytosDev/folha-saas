package com.sacolao.common.util;

public final class CpfValidator {

    private CpfValidator() {
    }

    public static String onlyDigits(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    public static boolean isValid(String value) {
        String cpf = onlyDigits(value);
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        try {
            int d1 = digit(cpf, 9, 10);
            int d2 = digit(cpf, 10, 11);
            return cpf.charAt(9) - '0' == d1 && cpf.charAt(10) - '0' == d2;
        } catch (Exception ex) {
            return false;
        }
    }

    private static int digit(String cpf, int length, int weightStart) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * (weightStart - i);
        }
        int mod = sum % 11;
        return mod < 2 ? 0 : 11 - mod;
    }
}
