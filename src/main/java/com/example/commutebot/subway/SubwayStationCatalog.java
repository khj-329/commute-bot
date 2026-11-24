package com.example.commutebot.subway;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 역 마스터 목록(subway-stations.csv: SUBWAY_ID,STATN_ID,STATN_NM,호선이름).
 * 역명은 STATN_NM 과 정확히 일치해야 등록할 수 있다.
 */
public class SubwayStationCatalog {

    static final String RESOURCE = "/subway-stations.csv";

    private record Entry(int subwayId, String stationId, String name, String line) {
    }

    private final List<Entry> entries;
    private final Map<String, Integer> lineToId = new LinkedHashMap<>();
    private final Map<Integer, String> idToLine = new LinkedHashMap<>();

    private SubwayStationCatalog(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        for (Entry e : entries) {
            lineToId.putIfAbsent(e.line(), e.subwayId());
            idToLine.putIfAbsent(e.subwayId(), e.line());
        }
    }

    public static SubwayStationCatalog fromClasspath() {
        try (InputStream in = SubwayStationCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("역 마스터 파일이 없습니다: " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<Entry> entries = new ArrayList<>();
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (header) {
                    header = false;
                    continue;
                }
                if (line.isEmpty()) {
                    continue;
                }
                List<String> cols = splitCsv(line);
                if (cols.size() < 4) {
                    continue;
                }
                entries.add(new Entry(Integer.parseInt(cols.get(0).trim()), cols.get(1).trim(),
                        cols.get(2).trim(), cols.get(3).trim()));
            }
            return new SubwayStationCatalog(entries);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 큰따옴표로 감싼 칸(쉼표 포함)을 처리하는 CSV 한 줄 분리. */
    static List<String> splitCsv(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }

    public int size() {
        return entries.size();
    }

    public boolean exists(String statnName, String lineName) {
        if (statnName == null || lineName == null) {
            return false;
        }
        String name = statnName.trim();
        String line = lineName.trim();
        return entries.stream().anyMatch(e -> e.name().equals(name) && e.line().equals(line));
    }

    public Optional<Integer> subwayIdOf(String lineName) {
        return Optional.ofNullable(lineName == null ? null : lineToId.get(lineName.trim()));
    }

    public Optional<String> lineNameOf(int subwayId) {
        return Optional.ofNullable(idToLine.get(subwayId));
    }

    /** 호선 이름 목록 (CSV 순서). */
    public List<String> lineNames() {
        return List.copyOf(lineToId.keySet());
    }

    /** 부분 일치 후보 "역명 호선". */
    public List<String> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String k = keyword.trim();
        return entries.stream()
                .filter(e -> e.name().contains(k))
                .map(e -> e.name() + " " + e.line())
                .distinct()
                .toList();
    }
}
