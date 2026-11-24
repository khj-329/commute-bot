package com.example.commutebot.airquality;

import com.example.commutebot.common.ApiCallException;

public class AirQualityException extends ApiCallException {

    public AirQualityException(String message, boolean authError, Throwable cause) {
        super(message, authError, cause);
    }
}
