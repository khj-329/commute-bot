package com.example.commutebot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class BotPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(BotProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void bindsKeysAndLocationUnderBotPrefix() {
        runner.withPropertyValues(
                        "bot.telegram-token=test-token",
                        "bot.public-api-key=public-key",
                        "bot.seoul-open-api-key=seoul-key",
                        "bot.location.lat=37.5665",
                        "bot.location.lon=126.9780",
                        "bot.location.air-station=은평구")
                .run(context -> {
                    BotProperties props = context.getBean(BotProperties.class);
                    assertThat(props.telegramToken()).isEqualTo("test-token");
                    assertThat(props.publicApiKey()).isEqualTo("public-key");
                    assertThat(props.seoulOpenApiKey()).isEqualTo("seoul-key");
                    assertThat(props.location().lat()).isEqualTo(37.5665);
                    assertThat(props.location().lon()).isEqualTo(126.9780);
                    assertThat(props.location().airStation()).isEqualTo("은평구");
                });
    }
}
