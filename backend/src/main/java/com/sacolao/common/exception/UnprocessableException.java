package com.sacolao.common.exception;

public class UnprocessableException extends BusinessException {

    public UnprocessableException(String code, String message) {
        super(code, message, 422);
    }
}
