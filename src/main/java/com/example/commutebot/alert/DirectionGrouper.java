package com.example.commutebot.alert;

import com.example.commutebot.subway.SubwayArrival;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 지하철 도착 정보를 방향별로 묶고, 방향마다 앞의 2대만 남긴다. */
public final class DirectionGrouper {

    static final int PER_DIRECTION = 2;

    private DirectionGrouper() {
    }

    public static Map<String, List<SubwayArrival>> topTwoPerDirection(List<SubwayArrival> arrivals) {
        Map<String, List<SubwayArrival>> grouped = new LinkedHashMap<>();
        for (SubwayArrival arrival : arrivals) {
            List<SubwayArrival> list = grouped.computeIfAbsent(arrival.direction(), k -> new ArrayList<>());
            if (list.size() < PER_DIRECTION) {
                list.add(arrival);
            }
        }
        return grouped;
    }
}
