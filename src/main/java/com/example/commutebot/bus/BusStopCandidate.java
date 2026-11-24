package com.example.commutebot.bus;

import java.util.List;
import java.util.stream.Collectors;

/** getStationByUid 응답을 정류소 ID(stId)로 묶은 후보 한 건. */
public record BusStopCandidate(String stId, String arsId, String stName, List<BusRouteAtStop> routes) {

    /** 후보 목록에 보여줄 방면 요약. */
    public String directionSummary() {
        return routes.stream()
                .map(BusRouteAtStop::direction)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    public List<String> routeNames() {
        return routes.stream().map(BusRouteAtStop::routeName).toList();
    }
}
