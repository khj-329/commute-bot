package com.example.commutebot.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commutebot.airquality.AirQualitySummary;
import com.example.commutebot.bus.BusArrival;
import com.example.commutebot.store.BusStop;
import com.example.commutebot.store.SubwayStation;
import com.example.commutebot.subway.SubwayArrival;
import com.example.commutebot.weather.WeatherSummary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AlertComposerTest {

    private final AlertComposer composer = new AlertComposer();

    private static final WeatherSummary WEATHER = new WeatherSummary("현재 3.2°C");
    private static final AirQualitySummary AIR = new AirQualitySummary("PM10 52㎍/㎥ 보통");
    private static final BusStop REP = new BusStop("13221", "112000138", "대표정류장", List.of("7017"), true);
    private static final BusStop TRANSFER = new BusStop("12345", "111000001", "환승정류장", List.of("701"), false);
    private static final SubwayStation SAEJEOL = new SubwayStation("새절(신사)", "6호선");

    private static SubwayArrival up(int minutes, String msg) {
        return new SubwayArrival("6호선", "상행", minutes, "응암", msg, 99);
    }

    @Test
    void missingRepresentativeStopAsksToSetIt() {
        String text = composer.compose(WEATHER, AIR, Optional.empty(), List.of(), List.of(), Map.of(), Map.of());

        assertThat(text).contains("대표 정류장을 설정해 주세요");
    }

    @Test
    void subwayKeepsTwoTrainsPerDirection() {
        Map<String, List<SubwayArrival>> subway = Map.of(SAEJEOL.key(), List.of(
                up(4, "4분 후 (증산(명지대앞))"),
                up(10, "10분 후 (디지털미디어시티)"),
                up(15, "15분 후 (월드컵경기장)")));

        String text = composer.compose(WEATHER, AIR, Optional.of(REP), List.of(), List.of(SAEJEOL),
                Map.of(REP.getStId(), List.of()), subway);

        assertThat(text).contains("4분 후 (증산(명지대앞))").contains("10분 후 (디지털미디어시티)")
                .doesNotContain("15분 후");
    }

    @Test
    void failedBusLookupShowsFailureAndKeepsOtherSections() {
        Map<String, List<BusArrival>> bus = new HashMap<>();
        bus.put(REP.getStId(), null);
        Map<String, List<SubwayArrival>> subway = Map.of(SAEJEOL.key(), List.of(up(4, "4분 후 (증산(명지대앞))")));

        String text = composer.compose(WEATHER, AIR, Optional.of(REP), List.of(), List.of(SAEJEOL), bus, subway);

        assertThat(text)
                .contains("대표정류장")
                .contains("조회 실패")
                .contains("현재 3.2°C")
                .contains("PM10 52㎍/㎥ 보통")
                .contains("4분 후 (증산(명지대앞))");
    }

    @Test
    void emptyArrivalsShowNoArrivalInfo() {
        String text = composer.compose(WEATHER, AIR, Optional.of(REP), List.of(), List.of(SAEJEOL),
                Map.of(REP.getStId(), List.of()), Map.of(SAEJEOL.key(), List.of()));

        assertThat(text).contains("도착 정보 없음");
        assertThat(text.split("도착 정보 없음", -1)).hasSize(3); // 버스 1, 지하철 1
    }

    @Test
    void failedWeatherAndAirShowFailure() {
        String text = composer.compose(null, null, Optional.empty(), List.of(), List.of(), Map.of(), Map.of());

        assertThat(text).contains("날씨: 조회 실패").contains("미세먼지: 조회 실패");
    }

    @Test
    void busArrivalsAreListedPerRoute() {
        Map<String, List<BusArrival>> bus = Map.of(
                REP.getStId(), List.of(new BusArrival("7017", "3분12초후[1번째 전]", "15분4초후[7번째 전]")),
                TRANSFER.getStId(), List.of(new BusArrival("701", "곧 도착", "운행종료")));

        String text = composer.compose(WEATHER, AIR, Optional.of(REP), List.of(TRANSFER), List.of(), bus, Map.of());

        assertThat(text)
                .contains("7017: 3분12초후[1번째 전] / 15분4초후[7번째 전]")
                .contains("701: 곧 도착 / 운행종료");
    }

    @Test
    void sectionsAreOrderedWeatherRepresentativeTransferSubway() {
        String text = composer.compose(WEATHER, AIR, Optional.of(REP), List.of(TRANSFER), List.of(SAEJEOL),
                Map.of(REP.getStId(), List.of(), TRANSFER.getStId(), List.of()),
                Map.of(SAEJEOL.key(), List.of()));

        int weather = text.indexOf("날씨");
        int air = text.indexOf("미세먼지");
        int rep = text.indexOf("대표 정류장");
        int transfer = text.indexOf("환승 정류장");
        int subway = text.indexOf("지하철");
        assertThat(weather).isGreaterThanOrEqualTo(0);
        assertThat(weather).isLessThan(air);
        assertThat(air).isLessThan(rep);
        assertThat(rep).isLessThan(transfer);
        assertThat(transfer).isLessThan(subway);
    }

    @Test
    void grouperKeepsAtMostTwoPerDirectionInOrder() {
        SubwayArrival down = new SubwayArrival("6호선", "하행", 3, "신내", "3분 후 (구산)", 99);
        Map<String, List<SubwayArrival>> grouped = DirectionGrouper.topTwoPerDirection(List.of(
                up(4, "a"), down, up(10, "b"), up(15, "c")));

        assertThat(grouped.keySet()).containsExactly("상행", "하행");
        assertThat(grouped.get("상행")).extracting(SubwayArrival::minutesLeft).containsExactly(4, 10);
        assertThat(grouped.get("하행")).containsExactly(down);
    }
}
