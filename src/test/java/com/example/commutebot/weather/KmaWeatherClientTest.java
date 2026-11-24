package com.example.commutebot.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.commutebot.config.BotProperties;
import com.example.commutebot.support.Fixtures;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KmaWeatherClientTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    private KmaWeatherClient clientAt(LocalDateTime seoulTime, String key) {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        Clock clock = Clock.fixed(seoulTime.atZone(SEOUL).toInstant(), SEOUL);
        BotProperties props = new BotProperties(null, key, null,
                new BotProperties.Location(37.5665, 126.9780, "은평구"));
        return new KmaWeatherClient(builder, props, clock);
    }

    private void expectNowcast(String baseTime, String fixture) {
        server.expect(requestTo(containsString("/getUltraSrtNcst")))
                .andExpect(queryParam("base_date", "20261124"))
                .andExpect(queryParam("base_time", baseTime))
                .andExpect(queryParam("nx", "60"))
                .andExpect(queryParam("ny", "127"))
                .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON));
    }

    private void expectForecast(String baseTime, String fixture) {
        server.expect(requestTo(containsString("/getVilageFcst")))
                .andExpect(queryParam("base_date", "20261124"))
                .andExpect(queryParam("base_time", baseTime))
                .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON));
    }

    @Test
    void summaryHasNowcastAndTodaysForecast() {
        KmaWeatherClient client = clientAt(LocalDateTime.of(2026, 11, 24, 7, 30), "plain-key");
        expectNowcast("0600", "kma-ncst-0600.json");
        expectForecast("0200", "kma-fcst-0200.json");
        expectForecast("0500", "kma-fcst-0500.json");

        String text = client.today().text();

        server.verify();
        assertThat(text)
                .contains("현재 3.2°C")
                .contains("습도 60%")
                .contains("강수 없음")
                .contains("최저 -1.0°C")      // TMN: base 0200
                .contains("최고 9.0°C")       // TMX: 가장 최근 발표(0500)
                .contains("구름많음")          // 08시 SKY: 최근 발표
                .contains("강수확률 30%");     // 08시 POP: 최근 발표
    }

    @Test
    void noPrecipitationTextIsTreatedAsZero() {
        KmaWeatherClient client = clientAt(LocalDateTime.of(2026, 11, 24, 7, 30), "plain-key");
        expectNowcast("0600", "kma-ncst-0600.json");
        expectForecast("0200", "kma-fcst-0200.json");
        expectForecast("0500", "kma-fcst-0500.json");

        String text = client.today().text();

        assertThat(text).contains("강수량 0mm").doesNotContain("강수없음");
    }

    @Test
    void afterLatestForecastLosesTodaysSlotsFallsBackToBase0200() {
        KmaWeatherClient client = clientAt(LocalDateTime.of(2026, 11, 24, 18, 30), "plain-key");
        expectNowcast("1700", "kma-ncst-1700.json");
        expectForecast("0200", "kma-fcst-0200.json");
        expectForecast("1700", "kma-fcst-1700.json");

        String text = client.today().text();

        assertThat(text)
                .contains("현재 6.1°C")
                .contains("비")
                .contains("최저 -1.0°C")
                .contains("최고 8.0°C")       // 1700 발표에는 오늘 TMX 가 없다
                .contains("맑음")
                .contains("강수확률 10%");
    }

    @Test
    void encodedAndDecodedKeysAreSentTheSame() {
        KmaWeatherClient client = clientAt(LocalDateTime.of(2026, 11, 24, 7, 30), "ab%2Bc%2Fd%3D%3D");
        server.expect(requestTo(containsString("/getUltraSrtNcst")))
                .andExpect(queryParam("serviceKey", "ab%2Bc%2Fd%3D%3D"))
                .andRespond(withSuccess(Fixtures.read("kma-ncst-0600.json"), MediaType.APPLICATION_JSON));
        expectForecast("0200", "kma-fcst-0200.json");
        expectForecast("0500", "kma-fcst-0500.json");

        client.today();
        server.verify();

        KmaWeatherClient decoded = clientAt(LocalDateTime.of(2026, 11, 24, 7, 30), "ab+c/d==");
        server.expect(requestTo(containsString("/getUltraSrtNcst")))
                .andExpect(queryParam("serviceKey", "ab%2Bc%2Fd%3D%3D"))
                .andRespond(withSuccess(Fixtures.read("kma-ncst-0600.json"), MediaType.APPLICATION_JSON));
        expectForecast("0200", "kma-fcst-0200.json");
        expectForecast("0500", "kma-fcst-0500.json");

        decoded.today();
        server.verify();
    }

    @Test
    void emptyResponseThrowsWeatherException() {
        KmaWeatherClient client = clientAt(LocalDateTime.of(2026, 11, 24, 7, 30), "plain-key");
        expectNowcast("0600", "kma-no-data.json");
        expectForecast("0200", "kma-fcst-0200.json");
        expectForecast("0500", "kma-fcst-0500.json");

        assertThatThrownBy(client::today).isInstanceOf(WeatherException.class);
    }

    @Test
    void authErrorIsReportedAsKeyProblem() {
        KmaWeatherClient client = clientAt(LocalDateTime.of(2026, 11, 24, 7, 30), "bad-key");
        server.expect(requestTo(containsString("/getUltraSrtNcst")))
                .andRespond(withSuccess(Fixtures.read("data-go-kr-auth-error.xml"), MediaType.TEXT_XML));
        expectForecast("0200", "kma-fcst-0200.json");
        expectForecast("0500", "kma-fcst-0500.json");

        assertThatThrownBy(client::today)
                .isInstanceOf(WeatherException.class)
                .hasMessageContaining("인증키")
                .satisfies(e -> assertThat(((WeatherException) e).isAuthError()).isTrue());
    }
}
