package com.example.commutebot.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.commutebot.airquality.AirQualitySummary;
import com.example.commutebot.alert.AlertService;
import com.example.commutebot.alert.AlertSnapshot;
import com.example.commutebot.bus.BusArrival;
import com.example.commutebot.bus.BusRouteAtStop;
import com.example.commutebot.bus.BusStopCandidate;
import com.example.commutebot.bus.BusStopLookup;
import com.example.commutebot.store.BusStop;
import com.example.commutebot.store.SubwayStation;
import com.example.commutebot.store.UserSettingsService;
import com.example.commutebot.subway.SubwayArrival;
import com.example.commutebot.subway.SubwayStationCatalog;
import com.example.commutebot.weather.WeatherSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({DashboardController.class, StopRegistrationController.class})
@Import(DashboardControllerTest.CatalogConfig.class)
class DashboardControllerTest {

    @TestConfiguration
    static class CatalogConfig {
        @Bean
        SubwayStationCatalog subwayStationCatalog() {
            return SubwayStationCatalog.fromClasspath();
        }
    }

    private static final BusStop REP = new BusStop("13221", "112000138", "대표정류장", List.of("7017"), true);
    private static final SubwayStation SAEJEOL = new SubwayStation("새절(신사)", "6호선");
    private static final WeatherSummary WEATHER = new WeatherSummary("현재 3.2°C, 오늘 최저 -1.0°C / 최고 9.0°C");
    private static final AirQualitySummary AIR = new AirQualitySummary("PM10 52㎍/㎥ 보통 · PM2.5 7㎍/㎥ 좋음");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AlertService alertService;

    @MockitoBean
    UserSettingsService settings;

    @MockitoBean
    BusStopLookup lookup;

    private static AlertSnapshot snapshot(WeatherSummary weather, AirQualitySummary air) {
        return new AlertSnapshot(weather, air, Optional.of(REP), List.of(), List.of(SAEJEOL),
                Map.of(REP.getStId(), List.of(new BusArrival("7017", "3분12초후[1번째 전]", "15분4초후[7번째 전]"))),
                Map.of(SAEJEOL.key(), List.of(
                        new SubwayArrival("6호선", "상행", 4, "응암", "4분 후 (증산(명지대앞))", 99))));
    }

    private static BusStopCandidate candidate(String stId, String name, String direction) {
        return new BusStopCandidate(stId, "13221", name,
                List.of(new BusRouteAtStop("100100409", "7017", 78, direction)));
    }

    @BeforeEach
    void setUp() {
        when(alertService.collect()).thenReturn(snapshot(WEATHER, AIR));
    }

    @Test
    void dashboardShowsAllSections() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("날씨"),
                        containsString("미세먼지"),
                        containsString("버스"),
                        containsString("지하철"),
                        containsString("현재 3.2°C"),
                        containsString("PM10 52㎍/㎥ 보통"),
                        containsString("3분12초후[1번째 전]"),
                        containsString("15분4초후[7번째 전]"),
                        containsString("4분 후 (증산(명지대앞))"))));
    }

    @Test
    void failedAirSectionShowsFailureAndWeatherStays() throws Exception {
        when(alertService.collect()).thenReturn(snapshot(WEATHER, null));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("조회 실패"),
                        containsString("현재 3.2°C"))));
    }

    @Test
    void singleCandidateIsSavedAsRepresentative() throws Exception {
        when(lookup.findByArsId("13221")).thenReturn(List.of(candidate("112000138", "대표정류장", "서울역")));

        mvc.perform(post("/stops/bus").param("arsId", "13221").param("representative", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(settings).setRepresentativeStop(eq("13221"), eq("112000138"), eq("대표정류장"), eq(List.of("7017")));
    }

    @Test
    void transferStopIsSavedWhenNotRepresentative() throws Exception {
        when(lookup.findByArsId("13221")).thenReturn(List.of(candidate("112000138", "대표정류장", "서울역")));

        mvc.perform(post("/stops/bus").param("arsId", "13221"))
                .andExpect(redirectedUrl("/"));

        verify(settings).addTransferStop("13221", "112000138", "대표정류장", List.of("7017"));
        verify(settings, never()).setRepresentativeStop(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void unknownArsIdIsNotSaved() throws Exception {
        when(lookup.findByArsId("99999")).thenReturn(List.of());

        mvc.perform(post("/stops/bus").param("arsId", "99999").param("representative", "true"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("error", containsString("서울 정류장만")));

        verify(settings, never()).setRepresentativeStop(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void nonNumericArsIdIsRejectedWithoutLookup() throws Exception {
        mvc.perform(post("/stops/bus").param("arsId", "abc"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("error", containsString("표지판 번호")));

        verify(lookup, never()).findByArsId(any());
    }

    @Test
    void multipleCandidatesGoToChooserAndChosenOneIsSaved() throws Exception {
        when(lookup.findByArsId("13221")).thenReturn(List.of(
                candidate("111000001", "정류장A", "북쪽방면"),
                candidate("111000002", "정류장B", "남쪽방면")));
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/stops/bus").session(session).param("arsId", "13221").param("representative", "true"))
                .andExpect(redirectedUrl("/stops/bus/choose"));
        verify(settings, never()).setRepresentativeStop(anyString(), anyString(), anyString(), anyList());

        mvc.perform(get("/stops/bus/choose").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("정류장A"), containsString("북쪽방면"),
                        containsString("정류장B"), containsString("남쪽방면"))));

        mvc.perform(post("/stops/bus/choose").session(session).param("stId", "111000002"))
                .andExpect(redirectedUrl("/"));
        verify(settings).setRepresentativeStop("13221", "111000002", "정류장B", List.of("7017"));
    }

    @Test
    void choosingAnIdOutsideTheCandidatesIsNotSaved() throws Exception {
        when(lookup.findByArsId("13221")).thenReturn(List.of(
                candidate("111000001", "정류장A", "북쪽방면"),
                candidate("111000002", "정류장B", "남쪽방면")));
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/stops/bus").session(session).param("arsId", "13221").param("representative", "true"));

        mvc.perform(post("/stops/bus/choose").session(session).param("stId", "999"))
                .andExpect(redirectedUrl("/stops/bus/choose"))
                .andExpect(flash().attribute("error", containsString("후보")));

        verify(settings, never()).setRepresentativeStop(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void unknownStationIsNotSavedAndCandidatesAreSuggested() throws Exception {
        mvc.perform(post("/stops/subway").param("name", "새절").param("line", "6호선"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("error", containsString("새절(신사) 6호선")));

        verify(settings, never()).addStation(anyString(), anyString());
    }

    @Test
    void unknownStationMessageIsRenderedOnDashboard() throws Exception {
        mvc.perform(get("/").flashAttr("error", "역 목록에 없는 역입니다. 후보: 새절(신사) 6호선"))
                .andExpect(content().string(containsString("역 목록에 없는 역입니다")));
    }

    @Test
    void exactStationIsSaved() throws Exception {
        mvc.perform(post("/stops/subway").param("name", "새절(신사)").param("line", "6호선"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", not(containsString("실패"))));

        verify(settings).addStation("새절(신사)", "6호선");
    }

    @Test
    void transferDeleteTargetsTheExactStop() throws Exception {
        mvc.perform(post("/stops/delete").param("type", "transfer")
                        .param("arsId", "99001").param("stId", "111000002"))
                .andExpect(redirectedUrl("/"));

        verify(settings).removeTransferStop("99001", "111000002");
        verify(settings, never()).removeTransferStop("99001");
    }

    @Test
    void deleteRemovesRegisteredItems() throws Exception {
        mvc.perform(post("/stops/delete").param("type", "transfer").param("arsId", "12345"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(post("/stops/delete").param("type", "subway").param("name", "새절(신사)").param("line", "6호선"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(post("/stops/delete").param("type", "representative"))
                .andExpect(redirectedUrl("/"));

        verify(settings).removeTransferStop("12345");
        verify(settings).removeStation("새절(신사)", "6호선");
        verify(settings).clearRepresentativeStop();
    }
}
