package com.example.commutebot.subway;

import java.util.List;

public interface SubwayArrivalProvider {

    /**
     * 역의 실시간 도착 정보 중 요청한 호선만. 데이터가 없으면 빈 목록.
     *
     * @throws SubwayApiException 인증 오류 등 호출 실패
     */
    List<SubwayArrival> arrivalsAt(String statnName, String lineName);
}
