package com.example.commutebot.alert;

import com.example.commutebot.airquality.AirQualityProvider;
import com.example.commutebot.airquality.AirQualitySummary;
import com.example.commutebot.bus.BusArrival;
import com.example.commutebot.bus.BusArrivalProvider;
import com.example.commutebot.bus.BusStopRef;
import com.example.commutebot.common.ApiCallException;
import com.example.commutebot.store.BusStop;
import com.example.commutebot.store.SubwayStation;
import com.example.commutebot.store.UserSettingsService;
import com.example.commutebot.subway.SubwayArrival;
import com.example.commutebot.subway.SubwayArrivalProvider;
import com.example.commutebot.weather.WeatherProvider;
import com.example.commutebot.weather.WeatherSummary;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 모든 Provider를 호출해 결과를 모은다.
 * <ul>
 *   <li>실패 격리: 한 Provider가 실패해도 나머지는 계속 호출하고, 실패 항목은 null(조회 실패)로 남긴다.</li>
 *   <li>재시도: 실패하면 한 번 더 호출한다.</li>
 *   <li>캐시: 날씨·미세먼지는 같은 날짜 안에서 CACHE_TTL 동안 재사용한다. 실패는 캐시하지 않는다.</li>
 * </ul>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    /** 같은 날이라도 현재 기온이 너무 오래되지 않도록 짧게 캐시한다. */
    static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final WeatherProvider weatherProvider;
    private final AirQualityProvider airProvider;
    private final BusArrivalProvider busProvider;
    private final SubwayArrivalProvider subwayProvider;
    private final UserSettingsService settings;
    private final AlertComposer composer;
    private final Clock clock;

    private final DailyCache<WeatherSummary> weatherCache = new DailyCache<>();
    private final DailyCache<AirQualitySummary> airCache = new DailyCache<>();

    public AlertService(WeatherProvider weatherProvider, AirQualityProvider airProvider,
            BusArrivalProvider busProvider, SubwayArrivalProvider subwayProvider,
            UserSettingsService settings, AlertComposer composer, Clock clock) {
        this.weatherProvider = weatherProvider;
        this.airProvider = airProvider;
        this.busProvider = busProvider;
        this.subwayProvider = subwayProvider;
        this.settings = settings;
        this.composer = composer;
        this.clock = clock;
    }

    /** 최종 메시지 문자열. */
    public String buildAlertMessage() {
        AlertSnapshot s = collect();
        return composer.compose(s.weather(), s.air(), s.representative(), s.transfers(), s.stations(),
                s.busByStop(), s.subwayByStation());
    }

    /** 섹션별 결과 (웹 화면용). */
    public AlertSnapshot collect() {
        WeatherSummary weather = weatherCache.get(clock, () -> callWithRetry("날씨", weatherProvider::today));
        AirQualitySummary air = airCache.get(clock, () -> callWithRetry("미세먼지", airProvider::now));

        Optional<BusStop> representative = settings.representativeStop();
        List<BusStop> transfers = settings.transferStops();
        List<SubwayStation> stations = settings.stations();

        List<BusStop> allStops = new ArrayList<>();
        representative.ifPresent(allStops::add);
        allStops.addAll(transfers);
        Map<String, List<BusArrival>> busByStop = new LinkedHashMap<>();
        for (BusStop stop : allStops) {
            BusStopRef ref = new BusStopRef(stop.getStId(), stop.getArsId(), stop.getStopName(), List.of());
            busByStop.put(stop.getStId(),
                    callWithRetry("버스 " + stop.getArsId(), () -> busProvider.arrivalsAt(ref)));
        }

        Map<String, List<SubwayArrival>> subwayByStation = new LinkedHashMap<>();
        for (SubwayStation station : stations) {
            subwayByStation.put(station.key(), callWithRetry("지하철 " + station.key(),
                    () -> subwayProvider.arrivalsAt(station.getName(), station.getLine())));
        }

        return new AlertSnapshot(weather, air, representative, transfers, stations,
                Collections.unmodifiableMap(busByStop), Collections.unmodifiableMap(subwayByStation));
    }

    /** 실패하면 한 번 더 호출한다. 두 번 다 실패하면 null. */
    private <T> T callWithRetry(String label, Supplier<T> call) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                boolean auth = e instanceof ApiCallException api && api.isAuthError();
                if (attempt == 2 || auth) {
                    if (auth) {
                        log.error("[인증키 오류] {} 조회 실패: {}", label, e.getMessage());
                    } else {
                        log.warn("{} 조회 실패 (재시도 후): {}", label, e.getMessage());
                    }
                    return null;
                }
                log.info("{} 조회 실패, 한 번 더 시도: {}", label, e.getMessage());
            }
        }
        return null;
    }

    /** 날짜와 TTL 기준 캐시. null(실패)은 저장하지 않는다. */
    private static final class DailyCache<T> {

        private T value;
        private LocalDate date;
        private Instant storedAt;

        synchronized T get(Clock clock, Supplier<T> loader) {
            LocalDate today = LocalDate.now(clock);
            Instant now = clock.instant();
            if (value != null && today.equals(date) && now.isBefore(storedAt.plus(CACHE_TTL))) {
                return value;
            }
            T loaded = loader.get();
            if (loaded != null) {
                value = loaded;
                date = today;
                storedAt = now;
            }
            return loaded;
        }
    }
}
