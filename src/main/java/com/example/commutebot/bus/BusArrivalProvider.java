package com.example.commutebot.bus;

import java.util.List;

public interface BusArrivalProvider {

    /**
     * 정류장의 노선별 도착 정보. 정류소·노선 없음, 운행 종료는 빈 목록.
     *
     * @throws BusApiException 인증 오류 등 호출 실패
     */
    List<BusArrival> arrivalsAt(BusStopRef stop);
}
