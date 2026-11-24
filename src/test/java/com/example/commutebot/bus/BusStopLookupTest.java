package com.example.commutebot.bus;

import static org.assertj.core.api.Assertions.assertThat;
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

class BusStopLookupTest {

    private MockRestServiceServer server;
    private BusStopLookup lookup;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        BotProperties props = new BotProperties(null, "plain-key", null,
                new BotProperties.Location(37.5, 126.9, "은평구"));
        lookup = new SeoulBusClient(builder, props);
    }

    private void respond(String arsId, String fixture) {
        server.expect(requestTo(containsString("/stationinfo/getStationByUid")))
                .andExpect(queryParam("arsId", arsId))
                .andExpect(queryParam("resultType", "json"))
                .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON));
    }

    @Test
    void singleStopWithTenRoutesIsOneCandidate() {
        respond("13221", "bus-station-by-uid-13221.json");

        List<BusStopCandidate> candidates = lookup.findByArsId("13221");

        assertThat(candidates).hasSize(1);
        BusStopCandidate stop = candidates.get(0);
        assertThat(stop.stId()).isEqualTo("112000138");
        assertThat(stop.arsId()).isEqualTo("13221");
        assertThat(stop.routes()).hasSize(10);
        assertThat(stop.routes())
                .filteredOn(r -> r.routeName().equals("7017"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.busRouteId()).isEqualTo("100100409");
                    assertThat(r.staOrd()).isEqualTo(78);
                });
    }

    @Test
    void differentStIdsBecomeSeparateCandidates() {
        respond("99001", "bus-station-by-uid-multi.json");

        List<BusStopCandidate> candidates = lookup.findByArsId("99001");

        assertThat(candidates).extracting(BusStopCandidate::stId).containsExactly("111000001", "111000002");
        assertThat(candidates.get(0).routes()).extracting(BusRouteAtStop::routeName).containsExactly("7017", "701");
        assertThat(candidates.get(0).directionSummary()).isEqualTo("북쪽방면");
        assertThat(candidates.get(1).stName()).isEqualTo("합성정류장B");
    }

    @Test
    void noResultIsEmptyList() {
        respond("99999", "bus-station-by-uid-empty.json");

        assertThat(lookup.findByArsId("99999")).isEmpty();
    }
}
