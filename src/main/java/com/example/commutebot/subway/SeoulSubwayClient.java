package com.example.commutebot.subway;

import com.example.commutebot.common.PublicApi;
import com.example.commutebot.config.BotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 서울 열린데이터광장 지하철 실시간 도착정보(realtimeStationArrival). 서울 열린데이터광장 키만 쓴다.
 * 인증키가 URL 경로에 들어가므로 URL을 로그에 남기지 않는다.
 */
@Component
public class SeoulSubwayClient implements SubwayArrivalProvider {

    static final String API_NAME = "서울 지하철";
    /** 환승역은 호선이 여러 개라 5건이면 방향별 2대를 채우지 못할 수 있어 20건을 받는다. */
    private static final String URL =
            "http://swopenAPI.seoul.go.kr/api/subway/{key}/json/realtimeStationArrival/0/20/{station}";

    private final RestClient client;
    private final BotProperties props;
    private final SubwayStationCatalog catalog;

    public SeoulSubwayClient(RestClient.Builder builder, BotProperties props, SubwayStationCatalog catalog) {
        this.client = builder.build();
        this.props = props;
        this.catalog = catalog;
    }

    @Override
    public List<SubwayArrival> arrivalsAt(String statnName, String lineName) {
        Optional<Integer> wanted = catalog.subwayIdOf(lineName);
        String key = props.seoulOpenApiKey() == null ? "" : props.seoulOpenApiKey().trim();
        Map<String, Object> vars = Map.of("key", key, "station", statnName.trim());
        String body = PublicApi.get(client, API_NAME, URL, vars, SubwayApiException::new);
        JsonNode root = PublicApi.json(API_NAME, body, SubwayApiException::new);

        JsonNode status = root.has("errorMessage") ? root.get("errorMessage") : root;
        String code = PublicApi.text(status, "code");
        if ("INFO-200".equals(code)) {
            return List.of();
        }
        if (!"INFO-000".equals(code)) {
            boolean auth = "INFO-100".equals(code);
            throw PublicApi.fail(API_NAME, code + " " + PublicApi.text(status, "message"), auth, null,
                    SubwayApiException::new);
        }

        List<SubwayArrival> result = new ArrayList<>();
        for (JsonNode item : root.path("realtimeArrivalList")) {
            int subwayId = parseInt(PublicApi.text(item, "subwayId"), 0);
            if (wanted.isPresent() && wanted.get() != subwayId) {
                continue;
            }
            int seconds = parseInt(PublicApi.text(item, "barvlDt"), 0);
            result.add(new SubwayArrival(
                    catalog.lineNameOf(subwayId).orElse(lineName),
                    PublicApi.text(item, "updnLine"),
                    seconds > 0 ? seconds / 60 : -1,
                    PublicApi.text(item, "bstatnNm"),
                    PublicApi.text(item, "arvlMsg2"),
                    parseInt(PublicApi.text(item, "arvlCd"), -1)));
        }
        return result;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
