package com.sacolao.common.exception;

public class BusinessException extends RuntimeException {

    private final String code;
    private final int status;

    public BusinessException(String code, String message) {
        this(code, message, 400);
    }

    public BusinessException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }
}
