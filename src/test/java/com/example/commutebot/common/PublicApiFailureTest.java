package com.example.commutebot.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commutebot.airquality.AirKoreaClient;
import com.example.commutebot.airquality.AirQualityException;
import com.example.commutebot.config.BotProperties;
import com.example.commutebot.config.HttpClientConfig;
import com.example.commutebot.subway.SeoulSubwayClient;
import com.example.commutebot.subway.SubwayApiException;
import com.example.commutebot.subway.SubwayStationCatalog;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PublicApiFailureTest {

    private static BotProperties props(String publicKey, String seoulKey) {
        return new BotProperties(null, publicKey, seoulKey, new BotProperties.Location(37.5, 126.9, "은평구"));
    }

    @Test
    void blankPublicKeyFailsAsKeyProblemWithoutCallingTheApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build(); // 요청이 오면 실패
        AirKoreaClient client = new AirKoreaClient(builder, props("  ", null));

        assertThatThrownBy(client::now)
                .isInstanceOf(AirQualityException.class)
                .hasMessageContaining("인증키")
                .satisfies(e -> assertThat(((AirQualityException) e).isAuthError()).isTrue());
        server.verify();
    }

    @Test
    void blankSeoulKeyFailsAsKeyProblemWithoutCallingTheApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SeoulSubwayClient client = new SeoulSubwayClient(builder, props("x", ""), SubwayStationCatalog.fromClasspath());

        assertThatThrownBy(() -> client.arrivalsAt("새절(신사)", "6호선"))
                .isInstanceOf(SubwayApiException.class)
                .hasMessageContaining("인증키")
                .satisfies(e -> assertThat(((SubwayApiException) e).isAuthError()).isTrue());
        server.verify();
    }

    @Test
    void hungApiTimesOutInsteadOfBlocking() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        slow.start();
        try {
            RestClient.Builder builder = RestClient.builder();
            new HttpClientConfig().timeoutCustomizer(Duration.ofMillis(300), Duration.ofMillis(300))
                    .customize(builder);
            RestClient client = builder.build();
            String url = "http://127.0.0.1:" + slow.getAddress().getPort() + "/x?k={k}";

            long start = System.nanoTime();
            assertThatThrownBy(() -> PublicApi.get(client, "느린 API", url, java.util.Map.of("k", "v"),
                    AirQualityException::new))
                    .isInstanceOf(AirQualityException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        } finally {
            slow.stop(0);
        }
    }
}
