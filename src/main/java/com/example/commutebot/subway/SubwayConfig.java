package com.example.commutebot.subway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SubwayConfig {

    @Bean
    public SubwayStationCatalog subwayStationCatalog() {
        return SubwayStationCatalog.fromClasspath();
    }
}
