package com.example.commutebot.web;

import com.example.commutebot.alert.AlertService;
import com.example.commutebot.alert.AlertSnapshot;
import com.example.commutebot.alert.DirectionGrouper;
import com.example.commutebot.bus.BusArrival;
import com.example.commutebot.store.BusStop;
import com.example.commutebot.store.SubwayStation;
import com.example.commutebot.subway.SubwayArrival;
import com.example.commutebot.subway.SubwayStationCatalog;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 1차 웹 화면: 날씨, 미세먼지, 대표·환승 정류장 버스, 지하철 도착을 섹션별로 보여준다. */
@Controller
public class DashboardController {

    /** 정류장 한 곳의 표시용 값. failed 이면 "조회 실패". */
    public record BusStopView(BusStop stop, boolean failed, List<BusArrival> arrivals) {
    }

    /** 역 한 곳의 표시용 값. 방향별 최대 2대. */
    public record StationView(SubwayStation station, boolean failed, Map<String, List<SubwayArrival>> byDirection) {
    }

    private final AlertService alertService;
    private final SubwayStationCatalog catalog;

    public DashboardController(AlertService alertService, SubwayStationCatalog catalog) {
        this.alertService = alertService;
        this.catalog = catalog;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        AlertSnapshot s = alertService.collect();
        model.addAttribute("weather", s.weather());
        model.addAttribute("air", s.air());
        model.addAttribute("representative", s.representative().map(stop -> busView(stop, s)).orElse(null));
        model.addAttribute("transfers", s.transfers().stream().map(stop -> busView(stop, s)).toList());
        model.addAttribute("stations", s.stations().stream().map(st -> stationView(st, s)).toList());
        model.addAttribute("lines", catalog.lineNames());
        return "dashboard";
    }

    private static BusStopView busView(BusStop stop, AlertSnapshot s) {
        List<BusArrival> arrivals = s.busByStop().get(stop.getStId());
        return new BusStopView(stop, arrivals == null, arrivals == null ? List.of() : arrivals);
    }

    private static StationView stationView(SubwayStation station, AlertSnapshot s) {
        List<SubwayArrival> arrivals = s.subwayByStation().get(station.key());
        return new StationView(station, arrivals == null,
                arrivals == null ? Map.of() : DirectionGrouper.topTwoPerDirection(arrivals));
    }
}
