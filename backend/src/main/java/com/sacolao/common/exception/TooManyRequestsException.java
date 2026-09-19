package com.sacolao.common.exception;

public class TooManyRequestsException extends BusinessException {

    public TooManyRequestsException(String message) {
        super("RATE_LIMITED", message, 429);
    }
}
