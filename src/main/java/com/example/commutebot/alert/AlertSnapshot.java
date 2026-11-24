package com.example.commutebot.alert;

import com.example.commutebot.airquality.AirQualitySummary;
import com.example.commutebot.bus.BusArrival;
import com.example.commutebot.store.BusStop;
import com.example.commutebot.store.SubwayStation;
import com.example.commutebot.subway.SubwayArrival;
import com.example.commutebot.weather.WeatherSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한 번 수집한 결과. 조회에 실패한 항목은 null (weather, air, 지도 값).
 *
 * @param busByStop       정류소 ID(stId) → 도착 목록
 * @param subwayByStation "역명|호선" → 도착 목록
 */
public record AlertSnapshot(
        WeatherSummary weather,
        AirQualitySummary air,
        Optional<BusStop> representative,
        List<BusStop> transfers,
        List<SubwayStation> stations,
        Map<String, List<BusArrival>> busByStop,
        Map<String, List<SubwayArrival>> subwayByStation) {
}
