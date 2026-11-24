package com.example.commutebot.subway;

import com.example.commutebot.common.ApiCallException;

public class SubwayApiException extends ApiCallException {

    public SubwayApiException(String message, boolean authError, Throwable cause) {
        super(message, authError, cause);
    }
}
