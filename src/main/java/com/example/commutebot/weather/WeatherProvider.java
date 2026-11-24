package com.example.commutebot.weather;

public interface WeatherProvider {

    /** @throws WeatherException 조회 실패 */
    WeatherSummary today();
}
