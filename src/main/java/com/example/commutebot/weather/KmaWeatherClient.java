package com.example.commutebot.weather;

import com.example.commutebot.common.PublicApi;
import com.example.commutebot.config.BotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 기상청 단기예보 조회서비스(VilageFcstInfoService_2.0).
 * <ul>
 *   <li>현재 기온(T1H)·습도(REH)·강수 형태(PTY): 초단기실황</li>
 *   <li>최저 기온(TMN): base 0200 단기예보</li>
 *   <li>최고 기온(TMX), 출근 시간대(08시) 하늘(SKY)·강수확률(POP)·강수량(PCP): 가장 최근 발표.
 *       최근 발표에 오늘 해당 슬롯이 없으면 base 0200 단기예보에서 읽는다.</li>
 * </ul>
 */
@Component
public class KmaWeatherClient implements WeatherProvider {

    static final String API_NAME = "날씨(기상청)";
    private static final String BASE = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";
    private static final String QUERY = "?serviceKey={key}&pageNo=1&numOfRows=1000&dataType=JSON"
            + "&base_date={date}&base_time={time}&nx={nx}&ny={ny}";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final List<Integer> FORECAST_HOURS = List.of(2, 5, 8, 11, 14, 17, 20, 23);
    private static final String COMMUTE_SLOT = "0800";
    /** 단기예보는 발표 시각 10분 후부터 제공된다. */
    private static final int FORECAST_DELAY_MIN = 10;
    /** 초단기실황은 매시 40분 무렵 생성된다. 여유를 두고 45분 이전에는 앞 시각을 쓴다. */
    private static final int NOWCAST_READY_MIN = 45;

    private final RestClient client;
    private final BotProperties props;
    private final Clock clock;

    public KmaWeatherClient(RestClient.Builder builder, BotProperties props, Clock clock) {
        this.client = builder.build();
        this.props = props;
        this.clock = clock;
    }

    private record Base(LocalDate date, int hour) {
        String dateText() {
            return date.format(DATE);
        }

        String timeText() {
            return String.format("%02d00", hour);
        }
    }

    @Override
    public WeatherSummary today() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        int[] grid = GridConverter.toGrid(props.location().lat(), props.location().lon());

        Map<String, String> nowcast = nowcast(now, grid);
        Base early = earlyForecastBase(now);
        Base latest = latestForecastBase(now);
        List<JsonNode> earlyItems = forecastItems(early, grid);
        List<JsonNode> latestItems = latest.equals(early) ? earlyItems : forecastItems(latest, grid);

        String todayText = today.format(DATE);
        String tmn = find(earlyItems, "TMN", todayText, null).orElse("-");
        String tmx = find(latestItems, "TMX", todayText, null)
                .or(() -> find(earlyItems, "TMX", todayText, null)).orElse("-");
        String sky = slot(latestItems, earlyItems, "SKY", todayText);
        String pop = slot(latestItems, earlyItems, "POP", todayText);
        String pcp = slot(latestItems, earlyItems, "PCP", todayText);

        String text = String.format("현재 %s°C, 습도 %s%%, %s · 오늘 최저 %s°C / 최고 %s°C · 출근(08시) %s, 강수확률 %s%%, 강수량 %s",
                nowcast.getOrDefault("T1H", "-"),
                nowcast.getOrDefault("REH", "-"),
                precipitationType(nowcast.get("PTY")),
                tmn, tmx,
                skyName(sky), pop, precipitationAmount(pcp));
        return new WeatherSummary(text);
    }

    private Map<String, String> nowcast(LocalDateTime now, int[] grid) {
        LocalDateTime baseTime = now.getMinute() < NOWCAST_READY_MIN ? now.minusHours(1) : now;
        Base base = new Base(baseTime.toLocalDate(), baseTime.getHour());
        Map<String, String> values = new HashMap<>();
        for (JsonNode item : items("/getUltraSrtNcst", base, grid)) {
            values.put(PublicApi.text(item, "category"), PublicApi.text(item, "obsrValue"));
        }
        return values;
    }

    private List<JsonNode> forecastItems(Base base, int[] grid) {
        return items("/getVilageFcst", base, grid);
    }

    private List<JsonNode> items(String operation, Base base, int[] grid) {
        Map<String, Object> vars = Map.of(
                "key", PublicApi.normalizeKey(props.publicApiKey()),
                "date", base.dateText(),
                "time", base.timeText(),
                "nx", grid[0],
                "ny", grid[1]);
        String body = PublicApi.get(client, API_NAME, BASE + operation + QUERY, vars, WeatherException::new);
        JsonNode root = PublicApi.json(API_NAME, body, WeatherException::new);
        JsonNode header = root.path("response").path("header");
        String code = PublicApi.text(header, "resultCode");
        if (!"00".equals(code)) {
            boolean auth = "30".equals(code) || "31".equals(code);
            throw PublicApi.fail(API_NAME, "resultCode " + code + " " + PublicApi.text(header, "resultMsg"),
                    auth, null, WeatherException::new);
        }
        JsonNode list = root.path("response").path("body").path("items").path("item");
        if (!list.isArray() || list.isEmpty()) {
            throw PublicApi.fail(API_NAME, "빈 응답 " + operation, false, null, WeatherException::new);
        }
        return toList(list);
    }

    private static List<JsonNode> toList(JsonNode array) {
        List<JsonNode> result = new java.util.ArrayList<>();
        array.forEach(result::add);
        return result;
    }

    /** 오늘 최저 기온용 base 0200. 02:10 이전이면 전날 2300 발표를 쓴다. */
    static Base earlyForecastBase(LocalDateTime now) {
        LocalDateTime ready = now.toLocalDate().atTime(2, FORECAST_DELAY_MIN);
        if (now.isBefore(ready)) {
            return new Base(now.toLocalDate().minusDays(1), 23);
        }
        return new Base(now.toLocalDate(), 2);
    }

    /** 지금 받을 수 있는 가장 최근 단기예보 발표 시각. */
    static Base latestForecastBase(LocalDateTime now) {
        LocalDateTime shifted = now.minusMinutes(FORECAST_DELAY_MIN);
        int hour = shifted.getHour();
        Integer best = null;
        for (int h : FORECAST_HOURS) {
            if (h <= hour) {
                best = h;
            }
        }
        if (best == null) {
            return new Base(shifted.toLocalDate().minusDays(1), 23);
        }
        return new Base(shifted.toLocalDate(), best);
    }

    private static String slot(List<JsonNode> latest, List<JsonNode> early, String category, String date) {
        return find(latest, category, date, COMMUTE_SLOT)
                .or(() -> find(early, category, date, COMMUTE_SLOT))
                .orElse("-");
    }

    private static Optional<String> find(List<JsonNode> items, String category, String date, String time) {
        return items.stream()
                .filter(i -> category.equals(PublicApi.text(i, "category")))
                .filter(i -> date.equals(PublicApi.text(i, "fcstDate")))
                .filter(i -> time == null || time.equals(PublicApi.text(i, "fcstTime")))
                .map(i -> PublicApi.text(i, "fcstValue"))
                .findFirst();
    }

    static String precipitationType(String pty) {
        if (pty == null) {
            return "강수 정보 없음";
        }
        return switch (pty) {
            case "0" -> "강수 없음";
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "5" -> "빗방울";
            case "6" -> "빗방울눈날림";
            case "7" -> "눈날림";
            default -> "강수 형태 " + pty;
        };
    }

    static String skyName(String sky) {
        return switch (sky) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> "하늘 정보 없음";
        };
    }

    /** PCP는 "강수없음" 같은 문자열이 오므로 숫자로 파싱하지 않는다. */
    static String precipitationAmount(String pcp) {
        if (pcp == null || pcp.isBlank() || "-".equals(pcp) || "강수없음".equals(pcp)) {
            return "0mm";
        }
        return pcp;
    }
}
