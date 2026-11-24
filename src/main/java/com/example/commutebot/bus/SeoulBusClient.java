package com.example.commutebot.bus;

import com.example.commutebot.common.PublicApi;
import com.example.commutebot.config.BotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 서울시 버스 정류소정보조회 getStationByUid. 표지판 번호 하나로 정류소 ID, 노선별 순번,
 * 노선별 도착 문구(arrmsg1, arrmsg2)를 한 번에 얻는다. getArrInfoByRouteList 는 쓰지 않는다.
 */
@Component
public class SeoulBusClient implements BusStopLookup, BusArrivalProvider {

    static final String API_NAME = "서울 버스";
    private static final String URL = "http://ws.bus.go.kr/api/rest/stationinfo/getStationByUid"
            + "?serviceKey={key}&arsId={arsId}&resultType=json";
    /** 3: 정류소 없음, 4: 노선/결과 없음, 8: 운행 종료 → 빈 결과. */
    private static final Set<String> EMPTY_CODES = Set.of("3", "4", "8");

    private final RestClient client;
    private final BotProperties props;

    public SeoulBusClient(RestClient.Builder builder, BotProperties props) {
        this.client = builder.build();
        this.props = props;
    }

    @Override
    public List<BusStopCandidate> findByArsId(String arsId) {
        Map<String, List<JsonNode>> byStId = new LinkedHashMap<>();
        for (JsonNode item : stationItems(arsId)) {
            byStId.computeIfAbsent(PublicApi.text(item, "stId"), k -> new ArrayList<>()).add(item);
        }
        List<BusStopCandidate> candidates = new ArrayList<>();
        byStId.forEach((stId, items) -> {
            JsonNode first = items.get(0);
            List<BusRouteAtStop> routes = items.stream().map(SeoulBusClient::route).toList();
            candidates.add(new BusStopCandidate(stId, PublicApi.text(first, "arsId"),
                    PublicApi.text(first, "stNm"), routes));
        });
        return candidates;
    }

    @Override
    public List<BusArrival> arrivalsAt(BusStopRef stop) {
        return stationItems(stop.arsId()).stream()
                .filter(item -> stop.stId().equals(PublicApi.text(item, "stId")))
                .map(item -> new BusArrival(
                        PublicApi.text(item, "rtNm"),
                        PublicApi.text(item, "arrmsg1"),
                        PublicApi.text(item, "arrmsg2")))
                .toList();
    }

    private List<JsonNode> stationItems(String arsId) {
        Map<String, Object> vars = Map.of(
                "key", PublicApi.normalizeKey(props.publicApiKey()),
                "arsId", arsId.trim());
        String body = PublicApi.get(client, API_NAME, URL, vars, BusApiException::new);
        JsonNode root = PublicApi.json(API_NAME, body, BusApiException::new);

        JsonNode header = root.path("msgHeader");
        String code = PublicApi.text(header, "headerCd");
        String message = PublicApi.text(header, "headerMsg");
        if (EMPTY_CODES.contains(code)) {
            return List.of();
        }
        if (!"0".equals(code)) {
            boolean auth = message.contains("인증") || message.contains("키") || message.toUpperCase().contains("KEY");
            throw PublicApi.fail(API_NAME, "headerCd " + code + " " + message, auth, null, BusApiException::new);
        }
        JsonNode list = root.path("msgBody").path("itemList");
        List<JsonNode> items = new ArrayList<>();
        if (list.isArray()) {
            list.forEach(items::add);
        }
        return items;
    }

    private static BusRouteAtStop route(JsonNode item) {
        int ord;
        try {
            ord = Integer.parseInt(PublicApi.text(item, "staOrd"));
        } catch (NumberFormatException e) {
            ord = 0;
        }
        return new BusRouteAtStop(PublicApi.text(item, "busRouteId"), PublicApi.text(item, "rtNm"), ord,
                PublicApi.text(item, "adirection"));
    }
}
