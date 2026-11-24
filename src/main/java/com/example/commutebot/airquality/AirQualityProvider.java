package com.example.commutebot.airquality;

public interface AirQualityProvider {

    /** @throws AirQualityException 조회 실패 */
    AirQualitySummary now();
}
