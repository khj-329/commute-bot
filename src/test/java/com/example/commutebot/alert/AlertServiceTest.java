package com.example.commutebot.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commutebot.airquality.AirQualityProvider;
import com.example.commutebot.airquality.AirQualitySummary;
import com.example.commutebot.bus.BusApiException;
import com.example.commutebot.bus.BusArrival;
import com.example.commutebot.bus.BusArrivalProvider;
import com.example.commutebot.store.BusStop;
import com.example.commutebot.store.SubwayStation;
import com.example.commutebot.store.UserSettingsService;
import com.example.commutebot.subway.SubwayArrival;
import com.example.commutebot.subway.SubwayArrivalProvider;
import com.example.commutebot.support.MutableClock;
import com.example.commutebot.weather.WeatherException;
import com.example.commutebot.weather.WeatherProvider;
import com.example.commutebot.weather.WeatherSummary;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

    private static final BusStop REP = new BusStop("13221", "112000138", "대표정류장", List.of("7017"), true);
    private static final BusStop TRANSFER = new BusStop("12345", "111000001", "환승정류장", List.of("701"), false);
    private static final SubwayStation SAEJEOL = new SubwayStation("새절(신사)", "6호선");

    private WeatherProvider weather;
    private AirQualityProvider air;
    private BusArrivalProvider bus;
    private SubwayArrivalProvider subway;
    private UserSettingsService settings;
    private MutableClock clock;
    private AlertService service;

    @BeforeEach
    void setUp() {
        weather = mock(WeatherProvider.class);
        air = mock(AirQualityProvider.class);
        bus = mock(BusArrivalProvider.class);
        subway = mock(SubwayArrivalProvider.class);
        settings = mock(UserSettingsService.class);
        clock = new MutableClock(LocalDateTime.of(2026, 11, 24, 7, 30), ZoneId.of("Asia/Seoul"));

        when(settings.representativeStop()).thenReturn(Optional.of(REP));
        when(settings.transferStops()).thenReturn(List.of(TRANSFER));
        when(settings.stations()).thenReturn(List.of(SAEJEOL));
        when(weather.today()).thenReturn(new WeatherSummary("현재 3.2°C"));
        when(air.now()).thenReturn(new AirQualitySummary("PM10 52㎍/㎥ 보통"));
        when(bus.arrivalsAt(argThat(s -> s != null && s.stId().equals(REP.getStId()))))
                .thenReturn(List.of(new BusArrival("7017", "3분후[1번째 전]", "15분후[7번째 전]")));
        when(bus.arrivalsAt(argThat(s -> s != null && s.stId().equals(TRANSFER.getStId()))))
                .thenReturn(List.of(new BusArrival("701", "곧 도착", "9분후[4번째 전]")));
        when(subway.arrivalsAt("새절(신사)", "6호선"))
                .thenReturn(List.of(new SubwayArrival("6호선", "상행", 4, "응암", "4분 후 (증산(명지대앞))", 99)));

        service = new AlertService(weather, air, bus, subway, settings, new AlertComposer(), clock);
    }

    @Test
    void weatherFailureDoesNotStopBusAndSubway() {
        when(weather.today()).thenThrow(new WeatherException("날씨 조회 실패", false, null));

        String message = service.buildAlertMessage();

        assertThat(message)
                .contains("날씨: 조회 실패")
                .contains("7017: 3분후[1번째 전] / 15분후[7번째 전]")
                .contains("701: 곧 도착")
                .contains("4분 후 (증산(명지대앞))")
                .contains("PM10 52㎍/㎥ 보통");
    }

    @Test
    void weatherAndAirAreCalledOnceForTwoBuildsOnTheSameDay() {
        service.buildAlertMessage();
        clock.advance(Duration.ofMinutes(5));
        service.buildAlertMessage();

        verify(weather, times(1)).today();
        verify(air, times(1)).now();
        verify(bus, times(4)).arrivalsAt(any()); // 버스는 캐시하지 않는다
    }

    @Test
    void cacheExpiresOnTheNextDay() {
        service.buildAlertMessage();
        clock.advance(Duration.ofDays(1));
        service.buildAlertMessage();

        verify(weather, times(2)).today();
    }

    @Test
    void cacheIsShortLivedWithinTheDay() {
        service.buildAlertMessage();
        clock.advance(AlertService.CACHE_TTL.plusMinutes(1));
        service.buildAlertMessage();

        verify(weather, times(2)).today();
    }

    @Test
    void failedCallIsRetriedOnce() {
        when(weather.today())
                .thenThrow(new WeatherException("일시 오류", false, null))
                .thenReturn(new WeatherSummary("현재 5.0°C"));

        String message = service.buildAlertMessage();

        assertThat(message).contains("현재 5.0°C");
        verify(weather, times(2)).today();
    }

    @Test
    void failuresAreNotCached() {
        when(weather.today()).thenThrow(new WeatherException("실패", false, null));
        service.buildAlertMessage();

        org.mockito.Mockito.doReturn(new WeatherSummary("현재 1.0°C")).when(weather).today();
        String message = service.buildAlertMessage();

        assertThat(message).contains("현재 1.0°C");
    }

    @Test
    void failingStopShowsFailureOnlyForThatStop() {
        when(bus.arrivalsAt(argThat(s -> s != null && s.stId().equals(TRANSFER.getStId()))))
                .thenThrow(new BusApiException("서울 버스 인증키 오류", true, null));

        AlertSnapshot snapshot = service.collect();

        assertThat(snapshot.busByStop().get(TRANSFER.getStId())).isNull();
        assertThat(snapshot.busByStop().get(REP.getStId())).hasSize(1);
        assertThat(service.buildAlertMessage()).contains("조회 실패").contains("7017:");
    }
}
