package com.example.commutebot.airquality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.commutebot.config.BotProperties;
import com.example.commutebot.support.Fixtures;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AirKoreaClientTest {

    private MockRestServiceServer server;
    private AirKoreaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BotProperties props = new BotProperties(null, "plain-key", null,
                new BotProperties.Location(37.5986, 126.9187, "은평구"));
        client = new AirKoreaClient(builder, props);
    }

    private void respond(String fixture) {
        server.expect(requestTo(containsString("/getMsrstnAcctoRltmMesureDnsty")))
                .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON));
    }

    @Test
    void requestsConfiguredStationWithVersion13() {
        server.expect(requestTo(containsString("/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty")))
                .andExpect(queryParam("stationName", "%EC%9D%80%ED%8F%89%EA%B5%AC")) // 은평구
                .andExpect(queryParam("dataTerm", "DAILY"))
                .andExpect(queryParam("numOfRows", "1"))
                .andExpect(queryParam("returnType", "json"))
                .andExpect(queryParam("ver", "1.3"))
                .andRespond(withSuccess(Fixtures.read("air-realtime-sample.json"), MediaType.APPLICATION_JSON));

        client.now();

        server.verify();
    }

    @Test
    void summaryHasPm10AndPm25ValuesWithGrades() {
        respond("air-realtime-sample.json");

        String text = client.now().text();

        assertThat(text)
                .contains("PM10 52㎍/㎥ 보통")
                .contains("PM2.5 7㎍/㎥ 좋음")
                .contains("은평구")
                .contains("2026-11-24 10:00");
    }

    @Test
    void midnightAs2400BecomesNextDayMidnight() {
        assertThat(AirKoreaClient.parseDataTime("2026-11-23 24:00"))
                .isEqualTo(LocalDateTime.of(2026, 11, 24, 0, 0));
        assertThat(AirKoreaClient.parseDataTime("2026-11-24 10:00"))
                .isEqualTo(LocalDateTime.of(2026, 11, 24, 10, 0));
    }

    @Test
    void dashOrBlankValueIsShownAsNoMeasurement() {
        respond("air-missing-values.json");

        String text = client.now().text();

        assertThat(text)
                .contains("PM10 측정값 없음")
                .contains("PM2.5 측정값 없음")
                .contains("2026-11-24 00:00");
    }

    @Test
    void emptyItemsThrowAirQualityException() {
        respond("air-empty.json");

        assertThatThrownBy(client::now).isInstanceOf(AirQualityException.class);
    }

    @Test
    void authErrorIsReportedAsKeyProblem() {
        server.expect(requestTo(containsString("/getMsrstnAcctoRltmMesureDnsty")))
                .andRespond(withSuccess(Fixtures.read("data-go-kr-auth-error.xml"), MediaType.TEXT_XML));

        assertThatThrownBy(client::now)
                .isInstanceOf(AirQualityException.class)
                .hasMessageContaining("인증키");
    }
}
