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
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 알림 메시지 조립. 섹션 순서: 날씨·미세먼지, 대표 정류장 버스, 환승 정류장 버스, 지하철.
 * 값이 null 이면 그 항목은 "조회 실패", 빈 목록이면 "도착 정보 없음".
 */
@Component
public class AlertComposer {

    static final String FAILED = "조회 실패";
    static final String NO_ARRIVAL = "도착 정보 없음";
    static final String SET_REPRESENTATIVE = "대표 정류장을 설정해 주세요";

    /**
     * @param busByStop       정류소 ID(stId) → 도착 목록 (null 이면 조회 실패)
     * @param subwayByStation "역명|호선" → 도착 목록 (null 이면 조회 실패)
     */
    public String compose(WeatherSummary weather, AirQualitySummary air, Optional<BusStop> representative,
            List<BusStop> transfers, List<SubwayStation> stations,
            Map<String, List<BusArrival>> busByStop, Map<String, List<SubwayArrival>> subwayByStation) {
        StringBuilder out = new StringBuilder();
        out.append("🌤 날씨: ").append(weather == null ? FAILED : weather.text()).append('\n');
        out.append("😷 미세먼지: ").append(air == null ? FAILED : air.text()).append('\n');

        out.append("\n🚌 대표 정류장\n");
        if (representative.isEmpty()) {
            out.append(SET_REPRESENTATIVE).append('\n');
        } else {
            appendStop(out, representative.get(), busByStop);
        }

        out.append("\n🔁 환승 정류장\n");
        if (transfers.isEmpty()) {
            out.append("등록된 환승 정류장 없음\n");
        } else {
            transfers.forEach(stop -> appendStop(out, stop, busByStop));
        }

        out.append("\n🚇 지하철\n");
        if (stations.isEmpty()) {
            out.append("등록된 역 없음\n");
        } else {
            stations.forEach(station -> appendStation(out, station, subwayByStation));
        }
        return out.toString().stripTrailing();
    }

    private static void appendStop(StringBuilder out, BusStop stop, Map<String, List<BusArrival>> busByStop) {
        out.append(stop.getStopName()).append(" (").append(stop.getArsId()).append(")\n");
        List<BusArrival> arrivals = busByStop.get(stop.getStId());
        if (arrivals == null) {
            out.append("  ").append(FAILED).append('\n');
        } else if (arrivals.isEmpty()) {
            out.append("  ").append(NO_ARRIVAL).append('\n');
        } else {
            for (BusArrival a : arrivals) {
                out.append("  ").append(a.routeName()).append(": ")
                        .append(a.firstArrivalText()).append(" / ").append(a.secondArrivalText()).append('\n');
            }
        }
    }

    private static void appendStation(StringBuilder out, SubwayStation station,
            Map<String, List<SubwayArrival>> subwayByStation) {
        out.append(station.getName()).append(' ').append(station.getLine()).append('\n');
        List<SubwayArrival> arrivals = subwayByStation.get(station.key());
        if (arrivals == null) {
            out.append("  ").append(FAILED).append('\n');
        } else if (arrivals.isEmpty()) {
            out.append("  ").append(NO_ARRIVAL).append('\n');
        } else {
            DirectionGrouper.topTwoPerDirection(arrivals).forEach((direction, list) ->
                    out.append("  ").append(direction).append(": ")
                            .append(list.stream().map(SubwayArrival::display).collect(Collectors.joining(", ")))
                            .append('\n'));
        }
    }
}
