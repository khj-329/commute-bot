package com.example.commutebot.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalTime;

/** 알림 설정. 단일 행(id 1)만 쓴다. */
@Entity
public class AlertSetting {

    static final long SINGLE_ID = 1L;

    @Id
    private Long id = SINGLE_ID;

    private LocalTime alertTime;

    protected AlertSetting() {
    }

    AlertSetting(LocalTime alertTime) {
        this.alertTime = alertTime;
    }

    public LocalTime getAlertTime() {
        return alertTime;
    }

    void setAlertTime(LocalTime alertTime) {
        this.alertTime = alertTime;
    }
}
