package com.sacolao.common.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, 403);
    }
}
