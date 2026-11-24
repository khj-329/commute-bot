package com.example.commutebot.subway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubwayStationCatalogTest {

    private final SubwayStationCatalog catalog = SubwayStationCatalog.fromClasspath();

    @Test
    void loadsAllStationsFromMasterCsv() {
        assertThat(catalog.size()).isEqualTo(696);
    }

    @Test
    void stationNameMustMatchExactly() {
        assertThat(catalog.exists("새절(신사)", "6호선")).isTrue();
        assertThat(catalog.exists("새절", "6호선")).isFalse();
        assertThat(catalog.exists("새절(신사)", "2호선")).isFalse();
    }

    @Test
    void gyeonggiSectionOfLine1IsSupported() {
        assertThat(catalog.exists("소요산", "1호선")).isTrue();
    }

    @Test
    void quotedNameWithCommaIsParsed() {
        assertThat(catalog.exists("남한산성입구(성남법원,검찰청)", "8호선")).isTrue();
    }

    @Test
    void lineNameMapsToSubwayIdAndBack() {
        assertThat(catalog.subwayIdOf("6호선")).contains(1006);
        assertThat(catalog.subwayIdOf("경의중앙선")).contains(1063);
        assertThat(catalog.subwayIdOf("없는선")).isEmpty();
        assertThat(catalog.lineNameOf(1065)).contains("공항철도");
    }

    @Test
    void searchFindsPartialMatchesWithLine() {
        assertThat(catalog.search("새절")).containsExactly("새절(신사) 6호선");
        assertThat(catalog.search("디지털미디어")).containsExactlyInAnyOrder(
                "디지털미디어시티 6호선", "디지털미디어시티 경의중앙선", "디지털미디어시티 공항철도");
        assertThat(catalog.search(" ")).isEmpty();
    }
}
