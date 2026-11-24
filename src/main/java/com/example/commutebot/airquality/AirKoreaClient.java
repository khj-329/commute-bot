package com.example.commutebot.airquality;

import com.example.commutebot.common.PublicApi;
import com.example.commutebot.config.BotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 에어코리아 측정소별 실시간 측정정보. 측정소는 설정값(bot.location.air-station)으로 고정한다.
 * ver=1.3 을 붙여야 PM2.5 필드가 응답에 포함된다.
 */
@Component
public class AirKoreaClient implements AirQualityProvider {

    static final String API_NAME = "미세먼지(에어코리아)";
    private static final String URL = "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty"
            + "?serviceKey={key}&stationName={station}&dataTerm=DAILY&numOfRows=1&pageNo=1&returnType=json&ver=1.3";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String NO_VALUE = "측정값 없음";

    private final RestClient client;
    private final BotProperties props;

    public AirKoreaClient(RestClient.Builder builder, BotProperties props) {
        this.client = builder.build();
        this.props = props;
    }

    @Override
    public AirQualitySummary now() {
        String station = props.location().airStation();
        Map<String, Object> vars = Map.of(
                "key", PublicApi.normalizeKey(props.publicApiKey()),
                "station", station);
        String body = PublicApi.get(client, API_NAME, URL, vars, AirQualityException::new);
        JsonNode root = PublicApi.json(API_NAME, body, AirQualityException::new);

        JsonNode header = root.path("response").path("header");
        String code = PublicApi.text(header, "resultCode");
        if (!"00".equals(code)) {
            boolean auth = "30".equals(code) || "31".equals(code);
            throw PublicApi.fail(API_NAME, "resultCode " + code + " " + PublicApi.text(header, "resultMsg"),
                    auth, null, AirQualityException::new);
        }
        JsonNode items = root.path("response").path("body").path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw PublicApi.fail(API_NAME, "측정소 " + station + " 응답 없음", false, null, AirQualityException::new);
        }
        JsonNode item = items.get(0);

        String time = formatTime(PublicApi.text(item, "dataTime"));
        String text = String.format("%s · %s (%s, %s 기준)",
                measurement("PM10", PublicApi.text(item, "pm10Value"), PublicApi.text(item, "pm10Grade1h")),
                measurement("PM2.5", PublicApi.text(item, "pm25Value"), PublicApi.text(item, "pm25Grade1h")),
                station, time);
        return new AirQualitySummary(text);
    }

    /** "yyyy-MM-dd HH:mm". 자정을 "24:00"으로 주므로 다음 날 00:00으로 바꾼다. */
    static LocalDateTime parseDataTime(String dataTime) {
        String value = dataTime.trim();
        if (value.endsWith(" 24:00")) {
            LocalDate date = LocalDate.parse(value.substring(0, value.length() - " 24:00".length()), DATE);
            return date.plusDays(1).atTime(LocalTime.MIDNIGHT);
        }
        return LocalDateTime.parse(value, DATE_TIME);
    }

    private static String formatTime(String dataTime) {
        if (dataTime.isBlank()) {
            return "측정 시각 없음";
        }
        try {
            return parseDataTime(dataTime).format(DATE_TIME);
        } catch (RuntimeException e) {
            return dataTime;
        }
    }

    private static String measurement(String label, String value, String grade) {
        if (value.isBlank() || "-".equals(value)) {
            return label + " " + NO_VALUE;
        }
        return label + " " + value + "㎍/㎥ " + gradeName(grade);
    }

    static String gradeName(String grade) {
        return switch (grade) {
            case "1" -> "좋음";
            case "2" -> "보통";
            case "3" -> "나쁨";
            case "4" -> "매우나쁨";
            default -> "(등급 없음)";
        };
    }
}
