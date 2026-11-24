package com.example.commutebot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 봇 설정. 실제 키와 토큰은 application-local.yml 에만 둔다.
 *
 * @param telegramToken   텔레그램 봇 토큰 (2차)
 * @param publicApiKey    공공데이터포털 키: 날씨, 미세먼지, 버스
 * @param seoulOpenApiKey 서울 열린데이터광장 키: 지하철 전용
 * @param location        날씨·미세먼지 기준 위치
 */
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        String telegramToken,
        String publicApiKey,
        String seoulOpenApiKey,
        Location location) {

    /**
     * @param lat        위도
     * @param lon        경도
     * @param airStation 에어코리아 측정소명 (거주 자치구 대표 측정소)
     */
    public record Location(double lat, double lon, String airStation) {
    }
}
