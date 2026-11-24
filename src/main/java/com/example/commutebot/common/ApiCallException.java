package com.example.commutebot.common;

/** 외부 API 호출 실패. 인증키 문제인지 구분한다. */
public class ApiCallException extends RuntimeException {

    private final boolean authError;

    public ApiCallException(String message, boolean authError, Throwable cause) {
        super(message, cause);
        this.authError = authError;
    }

    public boolean isAuthError() {
        return authError;
    }
}
