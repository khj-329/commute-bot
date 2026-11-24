package com.example.commutebot.store;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 설정(정류장, 역, 알림 시간) 저장소. */
@Service
@Transactional
public class UserSettingsService {

    static final LocalTime DEFAULT_ALERT_TIME = LocalTime.of(8, 0);

    private final BusStopRepository busStops;
    private final SubwayStationRepository stations;
    private final AlertSettingRepository alertSettings;

    UserSettingsService(BusStopRepository busStops, SubwayStationRepository stations,
            AlertSettingRepository alertSettings) {
        this.busStops = busStops;
        this.stations = stations;
        this.alertSettings = alertSettings;
    }

    /** 대표 정류장을 덮어쓴다. */
    public void setRepresentativeStop(String arsId, String stId, String stopName, List<String> routes) {
        busStops.deleteByRepresentativeTrue();
        busStops.flush();
        busStops.save(new BusStop(arsId, stId, stopName, routes, true));
    }

    @Transactional(readOnly = true)
    public Optional<BusStop> representativeStop() {
        return busStops.findFirstByRepresentativeTrue();
    }

    /** 대표 정류장을 지운다. */
    public void clearRepresentativeStop() {
        busStops.deleteByRepresentativeTrue();
    }

    /** 환승 정류장을 추가한다. 같은 (표지판 번호, 정류소 ID)는 한 번만 저장한다. */
    public void addTransferStop(String arsId, String stId, String stopName, List<String> routes) {
        boolean exists = transferStops().stream()
                .anyMatch(s -> s.getArsId().equals(arsId) && s.getStId().equals(stId));
        if (!exists) {
            busStops.save(new BusStop(arsId, stId, stopName, routes, false));
        }
    }

    public void removeTransferStop(String arsId) {
        busStops.deleteByRepresentativeFalseAndArsId(arsId);
    }

    @Transactional(readOnly = true)
    public List<BusStop> transferStops() {
        return busStops.findByRepresentativeFalseOrderByIdAsc();
    }

    /** 같은 (역 이름, 호선)은 한 번만 저장한다. */
    public void addStation(String name, String line) {
        if (!stations.existsByNameAndLine(name, line)) {
            stations.save(new SubwayStation(name, line));
        }
    }

    public void removeStation(String name, String line) {
        stations.deleteByNameAndLine(name, line);
    }

    @Transactional(readOnly = true)
    public List<SubwayStation> stations() {
        return stations.findAllByOrderByIdAsc();
    }

    /** 저장값이 없으면 08:00. */
    @Transactional(readOnly = true)
    public LocalTime alertTime() {
        return alertSettings.findById(AlertSetting.SINGLE_ID)
                .map(AlertSetting::getAlertTime)
                .orElse(DEFAULT_ALERT_TIME);
    }

    public void setAlertTime(LocalTime time) {
        AlertSetting setting = alertSettings.findById(AlertSetting.SINGLE_ID)
                .orElseGet(() -> new AlertSetting(time));
        setting.setAlertTime(time);
        alertSettings.save(setting);
    }
}
