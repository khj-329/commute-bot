package com.example.commutebot.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 등록한 지하철역. 같은 이름의 역이 있을 수 있어 호선과 함께 저장한다. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"name", "line"}))
public class SubwayStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String line;

    protected SubwayStation() {
    }

    public SubwayStation(String name, String line) {
        this.name = name;
        this.line = line;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLine() {
        return line;
    }

    /** 결과 지도의 키: 역명|호선. */
    public String key() {
        return name + "|" + line;
    }
}
