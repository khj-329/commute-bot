package com.example.commutebot.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** 외부 API 호출 타임아웃. 응답이 없는 API 하나가 화면 전체를 멈추지 않게 한다. */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer apiTimeoutCustomizer(
            @Value("${bot.http.connect-timeout:3s}") Duration connectTimeout,
            @Value("${bot.http.read-timeout:5s}") Duration readTimeout) {
        return timeoutCustomizer(connectTimeout, readTimeout);
    }

    public RestClientCustomizer timeoutCustomizer(Duration connectTimeout, Duration readTimeout) {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout);
            factory.setReadTimeout(readTimeout);
            builder.requestFactory(factory);
        };
    }
}
