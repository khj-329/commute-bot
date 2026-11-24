package com.example.commutebot.subway;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 지하철 도착 한 건.
 *
 * @param line           호선 이름 (subwayId 기준)
 * @param direction      updnLine (상행/하행, 내선/외선)
 * @param minutesLeft    barvlDt / 60. barvlDt 가 0이면 -1
 * @param destination    종착역 bstatnNm
 * @param arrivalMessage arvlMsg2
 * @param arrivalCode    arvlCd
 */
public record SubwayArrival(String line, String direction, int minutesLeft, String destination,
        String arrivalMessage, int arrivalCode) {

    /** 마지막 괄호 묶음: "4분 후 (증산(명지대앞))" → "증산(명지대앞)". */
    private static final Pattern LAST_PAREN = Pattern.compile("\\(((?:[^()]|\\([^()]*\\))*)\\)\\s*$");

    /** minutesLeft >= 0 이면 "N분 후 (전역명)", -1 이면 arvlMsg2 그대로. */
    public String display() {
        if (minutesLeft < 0) {
            return arrivalMessage;
        }
        String station = previousStation();
        return station == null ? minutesLeft + "분 후" : minutesLeft + "분 후 (" + station + ")";
    }

    private String previousStation() {
        if (arrivalMessage == null) {
            return null;
        }
        Matcher m = LAST_PAREN.matcher(arrivalMessage);
        return m.find() ? m.group(1) : null;
    }
}
