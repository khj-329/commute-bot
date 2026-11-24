package com.example.commutebot.bus;

/** 노선별 첫 번째·두 번째 도착 문구 (arrmsg1, arrmsg2). */
public record BusArrival(String routeName, String firstArrivalText, String secondArrivalText) {
}
