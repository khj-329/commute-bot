package com.example.commutebot.bus;

import java.util.List;

/** 도착 정보를 조회할 정류장 (저장된 정류장에서 만든다). */
public record BusStopRef(String stId, String arsId, String stName, List<BusRouteAtStop> routes) {
}
