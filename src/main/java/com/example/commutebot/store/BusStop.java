package com.example.commutebot.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.Arrays;
import java.util.List;

/** 등록한 버스 정류장. 대표 정류장은 한 건, 환승 정류장은 여러 건. */
@Entity
public class BusStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 표지판 번호(ARS). */
    @Column(nullable = false)
    private String arsId;

    /** 정류소 ID. 같은 표지판 번호에 정류소가 여러 개일 때 구분한다. */
    @Column(nullable = false)
    private String stId;

    private String stopName;

    /** 경유 노선 이름. 쉼표로 구분해 저장한다. */
    @Column(length = 2000)
    private String routes;

    private boolean representative;

    protected BusStop() {
    }

    BusStop(String arsId, String stId, String stopName, List<String> routes, boolean representative) {
        this.arsId = arsId;
        this.stId = stId;
        this.stopName = stopName;
        this.routes = String.join(",", routes);
        this.representative = representative;
    }

    public Long getId() {
        return id;
    }

    public String getArsId() {
        return arsId;
    }

    public String getStId() {
        return stId;
    }

    public String getStopName() {
        return stopName;
    }

    public List<String> getRoutes() {
        if (routes == null || routes.isBlank()) {
            return List.of();
        }
        return Arrays.asList(routes.split(","));
    }

    public boolean isRepresentative() {
        return representative;
    }
}
