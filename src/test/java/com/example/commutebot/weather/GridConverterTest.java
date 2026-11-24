package com.example.commutebot.weather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GridConverterTest {

    @Test
    void seoulCityHallMapsToOfficialGrid() {
        assertThat(GridConverter.toGrid(37.5665, 126.9780)).containsExactly(60, 127);
    }

    @Test
    void eungamDongMapsToEunpyeongGrid() {
        // 응암동 행정복지센터 부근 좌표. 기상청 격자 좌표표의 은평구 응암동 값은 (59, 127).
        assertThat(GridConverter.toGrid(37.5986, 126.9187)).containsExactly(59, 127);
    }

    @Test
    void busanCityHallMapsToOfficialGrid() {
        // 기상청 격자 좌표표: 부산광역시 연제구 연산동(부산시청) (98, 76)
        assertThat(GridConverter.toGrid(35.1798, 129.0750)).containsExactly(98, 76);
    }
}
