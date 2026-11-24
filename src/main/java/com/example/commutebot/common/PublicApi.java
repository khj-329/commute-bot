package com.example.commutebot.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 공공 API 호출 공통 처리: 인증키 정규화, 응답 본문 수신, 인증 오류 판별, JSON 파싱.
 * 로그에는 URL(인증키 포함)을 남기지 않는다.
 */
public final class PublicApi {

    private static final Logger log = LoggerFactory.getLogger(PublicApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 예외 생성기: (메시지, 인증 오류 여부, 원인) → 각 Provider의 예외. */
    @FunctionalInterface
    public interface ErrorFactory<E extends ApiCallException> {
        E create(String message, boolean authError, Throwable cause);
    }

    private PublicApi() {
    }

    /**
     * 공공데이터포털은 Encoding 키와 Decoding 키를 함께 준다. 어느 쪽을 설정해도 되도록
     * 이미 인코딩된 키(%XX 포함)는 디코딩해 두고, 요청 시 URI 변수로 한 번만 인코딩되게 한다.
     */
    public static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        String key = raw.trim();
        if (key.contains("%")) {
            return URLDecoder.decode(key, StandardCharsets.UTF_8);
        }
        return key;
    }

    /** 인증키 일부만 남긴 문자열. */
    public static String mask(String key) {
        if (key == null || key.length() <= 4) {
            return "****";
        }
        return key.substring(0, 4) + "****";
    }

    /**
     * URI 템플릿과 변수로 GET 요청을 보내고 본문을 문자열로 받는다.
     * 변수 값은 모두 인코딩된다 (인증키의 +, /, = 포함).
     */
    public static <E extends ApiCallException> String get(RestClient client, String apiName,
            String uriTemplate, Map<String, ?> vars, ErrorFactory<E> errors) {
        String body;
        try {
            body = client.get().uri(uriTemplate, vars).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean auth = status == 401 || status == 403 || looksLikeAuthError(e.getResponseBodyAsString());
            throw fail(apiName, "HTTP " + status, auth, e, errors);
        } catch (RestClientException e) {
            throw fail(apiName, "호출 오류 " + e.getClass().getSimpleName(), false, e, errors);
        }
        if (body == null || body.isBlank()) {
            throw fail(apiName, "빈 응답", false, null, errors);
        }
        if (looksLikeAuthError(body)) {
            throw fail(apiName, "인증 실패 응답", true, null, errors);
        }
        return body;
    }

    /** 본문을 JSON으로 읽는다. JSON이 아니면 실패. */
    public static <E extends ApiCallException> JsonNode json(String apiName, String body, ErrorFactory<E> errors) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw fail(apiName, "JSON 아닌 응답", false, e, errors);
        }
    }

    /** 실패를 로그로 남기고 예외를 만든다. 인증 오류는 로그에서 구분된다. */
    public static <E extends ApiCallException> E fail(String apiName, String reason, boolean auth,
            Throwable cause, ErrorFactory<E> errors) {
        String message;
        if (auth) {
            message = apiName + " 인증키 오류: " + reason
                    + " (공공데이터포털 Encoding/Decoding 키, 활용 승인 여부를 확인하세요)";
            log.error("[인증키 오류] {}", message);
        } else {
            message = apiName + " 조회 실패: " + reason;
            log.warn("[API 오류] {}", message);
        }
        return errors.create(message, auth, cause);
    }

    static boolean looksLikeAuthError(String body) {
        if (body == null) {
            return false;
        }
        String upper = body.toUpperCase(Locale.ROOT);
        return upper.contains("SERVICE_KEY")
                || upper.contains("SERVICE KEY")
                || upper.contains("UNAUTHORIZED")
                || upper.contains("INFO-100")
                || body.contains("인증키");
    }

    /** JSON 노드의 텍스트 값. 없으면 빈 문자열. */
    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText().trim();
    }
}
