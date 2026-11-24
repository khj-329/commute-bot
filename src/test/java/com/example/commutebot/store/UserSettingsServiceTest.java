package com.example.commutebot.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(UserSettingsService.class)
class UserSettingsServiceTest {

    @Autowired
    UserSettingsService service;

    @Test
    void representativeStopKeepsOnlyTheLastValue() {
        service.setRepresentativeStop("13221", "112000138", "첫 정류장", List.of("7017", "750A"));
        service.setRepresentativeStop("12345", "111000001", "둘째 정류장", List.of("701"));

        BusStop stop = service.representativeStop().orElseThrow();
        assertThat(stop.getArsId()).isEqualTo("12345");
        assertThat(stop.getStId()).isEqualTo("111000001");
        assertThat(stop.getStopName()).isEqualTo("둘째 정류장");
        assertThat(stop.getRoutes()).containsExactly("701");
        assertThat(service.transferStops()).isEmpty();
    }

    @Test
    void sameStationAndLineIsStoredOnce() {
        service.addStation("새절(신사)", "6호선");
        service.addStation("새절(신사)", "6호선");
        service.addStation("새절(신사)", "경의중앙선");

        assertThat(service.stations())
                .extracting(SubwayStation::getName, SubwayStation::getLine)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("새절(신사)", "6호선"),
                        org.assertj.core.groups.Tuple.tuple("새절(신사)", "경의중앙선"));
    }

    @Test
    void removeStationDeletesOnlyThatLine() {
        service.addStation("새절(신사)", "6호선");
        service.addStation("소요산", "1호선");

        service.removeStation("새절(신사)", "6호선");

        assertThat(service.stations()).extracting(SubwayStation::getName).containsExactly("소요산");
    }

    @Test
    void alertTimeDefaultsToEightAndCanBeChanged() {
        assertThat(service.alertTime()).isEqualTo(LocalTime.of(8, 0));

        service.setAlertTime(LocalTime.of(7, 30));

        assertThat(service.alertTime()).isEqualTo(LocalTime.of(7, 30));
    }

    @Test
    void transferStopsAreAddedAndRemovedByArsId() {
        service.setRepresentativeStop("13221", "112000138", "대표", List.of("7017"));
        service.addTransferStop("12345", "111000001", "환승1", List.of("701", "702"));
        service.addTransferStop("12346", "111000002", "환승2", List.of("703"));

        service.removeTransferStop("12345");

        assertThat(service.transferStops()).extracting(BusStop::getArsId).containsExactly("12346");
        assertThat(service.transferStops().get(0).getRoutes()).containsExactly("703");
        assertThat(service.representativeStop()).isPresent();
    }
}
