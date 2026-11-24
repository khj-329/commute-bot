package com.example.commutebot;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commutebot.alert.AlertService;
import com.example.commutebot.bus.BusArrivalProvider;
import com.example.commutebot.bus.BusStopLookup;
import com.example.commutebot.subway.SubwayStationCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** 전체 빈 연결 확인. 외부 API는 호출하지 않는다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CommuteBotApplicationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextWiresAllProvidersAndServices() {
        assertThat(context.getBean(AlertService.class)).isNotNull();
        assertThat(context.getBean(BusStopLookup.class)).isSameAs(context.getBean(BusArrivalProvider.class));
        assertThat(context.getBean(SubwayStationCatalog.class).size()).isEqualTo(696);
    }
}
