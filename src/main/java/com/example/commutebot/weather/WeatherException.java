package com.example.commutebot.weather;

import com.example.commutebot.common.ApiCallException;

public class WeatherException extends ApiCallException {

    public WeatherException(String message, boolean authError, Throwable cause) {
        super(message, authError, cause);
    }
}
