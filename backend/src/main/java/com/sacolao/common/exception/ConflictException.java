package com.sacolao.common.exception;

public class ConflictException extends BusinessException {

    public ConflictException(String code, String message) {
        super(code, message, 409);
    }
}
