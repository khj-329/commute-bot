package com.example.commutebot.subway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.commutebot.config.BotProperties;
import com.example.commutebot.support.Fixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SeoulSubwayClientTest {

    private static final SubwayStationCatalog CATALOG = SubwayStationCatalog.fromClasspath();

    private MockRestServiceServer server;
    private SeoulSubwayClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BotProperties props = new BotProperties(null, null, "seoul-key",
                new BotProperties.Location(37.5, 126.9, "은평구"));
        client = new SeoulSubwayClient(builder, props, CATALOG);
    }

    private void respond(String fixture) {
        server.expect(requestTo(containsString("/realtimeStationArrival/")))
                .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON));
    }

    @Test
    void requestUsesSeoulKeyJsonAndEncodedStationName() {
        server.expect(requestTo(containsString(
                        "/api/subway/seoul-key/json/realtimeStationArrival/0/20/%EC%83%88%EC%A0%88%28%EC%8B%A0%EC%82%AC%29")))
                .andRespond(withSuccess(Fixtures.read("subway-realtime-sample.json"), MediaType.APPLICATION_JSON));

        client.arrivalsAt("새절(신사)", "6호선");

        server.verify();
    }

    @Test
    void parsesFourArrivalsWithMinutesAndDirection() {
        respond("subway-realtime-sample.json");

        List<SubwayArrival> arrivals = client.arrivalsAt("새절(신사)", "6호선");

        assertThat(arrivals).hasSize(4);
        SubwayArrival first = arrivals.get(0);
        assertThat(first.line()).isEqualTo("6호선");
        assertThat(first.direction()).isEqualTo("상행");
        assertThat(first.minutesLeft()).isEqualTo(4);
        assertThat(first.destination()).isEqualTo("응암");
        assertThat(first.arrivalCode()).isEqualTo(99);
        assertThat(first.display()).isEqualTo("4분 후 (증산(명지대앞))");
    }

    @Test
    void onlyRequestedLineIsKept() {
        respond("subway-realtime-dmc.json");

        List<SubwayArrival> arrivals = client.arrivalsAt("디지털미디어시티", "6호선");

        assertThat(arrivals).hasSize(2).allSatisfy(a -> assertThat(a.line()).isEqualTo("6호선"));
    }

    @Test
    void zeroBarvlDtShowsArrivalMessageAsIs() {
        respond("subway-realtime-soyosan.json");

        List<SubwayArrival> arrivals = client.arrivalsAt("소요산", "1호선");

        assertThat(arrivals).hasSize(5);
        assertThat(arrivals.get(0).minutesLeft()).isEqualTo(-1);
        assertThat(arrivals.get(0).display()).isEqualTo("[7]번째 전역 (양주)");
    }

    @Test
    void noDataIsEmptyList() {
        respond("subway-no-data.json");

        assertThat(client.arrivalsAt("새절(신사)", "6호선")).isEmpty();
    }

    @Test
    void authErrorThrowsSubwayApiException() {
        respond("subway-auth-error.json");

        assertThatThrownBy(() -> client.arrivalsAt("새절(신사)", "6호선"))
                .isInstanceOf(SubwayApiException.class)
                .hasMessageContaining("인증키")
                .hasMessageNotContaining("seoul-key");
    }
}
