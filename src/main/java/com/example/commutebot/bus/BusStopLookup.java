package com.example.commutebot.bus;

import java.util.List;

public interface BusStopLookup {

    /**
     * 표지판 번호로 정류소 후보를 찾는다. 결과가 없으면 빈 목록.
     *
     * @throws BusApiException 인증 오류 등 호출 실패
     */
    List<BusStopCandidate> findByArsId(String arsId);
}
