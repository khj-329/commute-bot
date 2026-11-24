package com.example.commutebot.bus;

/**
 * 정류소를 지나는 노선 하나.
 *
 * @param staOrd    해당 정류소의 노선별 순번(ord)
 * @param direction 방면(adirection)
 */
public record BusRouteAtStop(String busRouteId, String routeName, int staOrd, String direction)
        implements java.io.Serializable {
}
