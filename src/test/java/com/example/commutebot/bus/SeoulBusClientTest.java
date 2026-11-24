package com.example.commutebot.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
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

class SeoulBusClientTest {

    private MockRestServiceServer server;
    private SeoulBusClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BotProperties props = new BotProperties(null, "plain-key", null,
                new BotProperties.Location(37.5, 126.9, "은평구"));
        client = new SeoulBusClient(builder, props);
    }

    private void respond(String fixture) {
        server.expect(requestTo(containsString("/stationinfo/getStationByUid")))
                .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON));
    }

    private static BusStopRef stop(String arsId, String stId) {
        return new BusStopRef(stId, arsId, "정류장", List.of());
    }

    @Test
    void arrivalsComeFromArrmsg1AndArrmsg2PerRoute() {
        server.expect(requestTo(containsString("/stationinfo/getStationByUid")))
                .andExpect(queryParam("arsId", "13221"))
                .andRespond(withSuccess(Fixtures.read("bus-station-by-uid-13221.json"), MediaType.APPLICATION_JSON));

        List<BusArrival> arrivals = client.arrivalsAt(stop("13221", "112000138"));

        assertThat(arrivals).hasSize(10);
        assertThat(arrivals.get(0)).isEqualTo(new BusArrival("7017", "3분12초후[1번째 전]", "15분4초후[7번째 전]"));
    }

    @Test
    void onlyRoutesOfTheRegisteredStIdAreReturned() {
        respond("bus-station-by-uid-multi.json");

        List<BusArrival> arrivals = client.arrivalsAt(stop("99001", "111000002"));

        assertThat(arrivals).containsExactly(new BusArrival("702A", "6분후[2번째 전]", "곧 도착"));
    }

    @Test
    void headerCode3IsEmptyResult() {
        respond("bus-station-by-uid-header3.json");

        assertThat(client.arrivalsAt(stop("13221", "112000138"))).isEmpty();
    }

    @Test
    void headerCode8QuotaExceededIsAFailureNotAnEmptyResult() {
        respond("bus-header8-quota.json");

        assertThatThrownBy(() -> client.arrivalsAt(stop("13221", "112000138")))
                .isInstanceOf(BusApiException.class)
                .hasMessageContaining("요청 제한")
                .satisfies(e -> assertThat(((BusApiException) e).isAuthError()).isFalse());
    }

    @Test
    void headerCode8OnLookupIsAFailureNotANonexistentStop() {
        respond("bus-header8-quota.json");

        assertThatThrownBy(() -> client.findByArsId("13221")).isInstanceOf(BusApiException.class);
    }

    @Test
    void headerCode22ServiceEndedIsEmptyResult() {
        respond("bus-header22-ended.json");

        assertThat(client.arrivalsAt(stop("13221", "112000138"))).isEmpty();
    }

    @Test
    void headerCode6UnregisteredKeyIsAuthError() {
        respond("bus-header6-unregistered.json");

        assertThatThrownBy(() -> client.arrivalsAt(stop("13221", "112000138")))
                .isInstanceOf(BusApiException.class)
                .hasMessageContaining("인증키")
                .satisfies(e -> assertThat(((BusApiException) e).isAuthError()).isTrue());
    }

    @Test
    void authErrorThrowsBusApiExceptionMentioningKey() {
        respond("bus-auth-error.json");

        assertThatThrownBy(() -> client.arrivalsAt(stop("13221", "112000138")))
                .isInstanceOf(BusApiException.class)
                .hasMessageContaining("인증키");
    }

    @Test
    void authErrorOnLookupAlsoThrows() {
        respond("bus-auth-error.json");

        assertThatThrownBy(() -> client.findByArsId("13221"))
                .isInstanceOf(BusApiException.class)
                .hasMessageContaining("인증키");
    }
}
