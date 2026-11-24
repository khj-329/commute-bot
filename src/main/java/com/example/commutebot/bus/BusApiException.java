package com.example.commutebot.bus;

import com.example.commutebot.common.ApiCallException;

public class BusApiException extends ApiCallException {

    public BusApiException(String message, boolean authError, Throwable cause) {
        super(message, authError, cause);
    }
}
