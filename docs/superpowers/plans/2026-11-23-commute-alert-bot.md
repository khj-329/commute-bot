# 출근길 알림 봇 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 1차: 날씨, 미세먼지, 대표·환승 정류장 버스 도착, 등록된 지하철역 도착 정보를 웹 화면에서 확인하는 Spring Boot 앱을 만든다 (Task 1~8, 8W). 2차: 같은 정보를 텔레그램으로 매일 알림 보내는 기능을 붙인다 (Task 9~11, 보류).

**Architecture:** 외부 API마다 Provider 인터페이스를 두고, 알림 조립기(AlertComposer)가 Provider 결과를 모아 메시지를 만든다. 사용자 설정(정류장, 역, 알림 시간)은 로컬 DB에 저장하고, 텔레그램 롱폴링으로 명령을 받는다. 알림 시간은 DB 값을 읽어 `TaskScheduler`에 동적으로 등록한다.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, SQLite(xerial JDBC), Spring `RestClient`, JUnit 5, Mockito, Gradle

**Spec:** `docs/superpowers/specs/2026-11-23-commute-alert-bot-design.md`

## Global Constraints

- 공공 API 인증키(공공데이터포털 키, 서울 열린데이터광장 키), 텔레그램 봇 토큰은 설정 파일(`application-local.yml`)에만 둔다. 코드와 저장소에 넣지 않는다.
- 공공데이터포털 키는 날씨, 미세먼지, 버스에 쓰고, 서울 열린데이터광장 키는 지하철(`swopenAPI.seoul.go.kr`)에만 쓴다.
- 평일(월~금)에만 알림을 보낸다.
- 알림 시간 기본값은 `08:00`, 형식은 `HH:MM`(24시간).
- 지하철은 방향별로 다음 열차 2개까지만 표시한다.
- 외부 호출 실패 시 재시도는 1회다. 실패한 항목은 `조회 실패`로 표시하고 나머지는 정상 발송한다.
- 날씨와 미세먼지는 같은 날 같은 위치에 대해 캐시한다.
- 미세먼지 측정소는 `bot.location.air-station`(거주 자치구의 대표 측정소, 현재 `은평구`)으로 고정한다. 근접 측정소 조회 API는 사용하지 않는다.
- 미세먼지 실시간 조회는 `dataTerm=DAILY&numOfRows=1&ver=1.3`으로 호출한다. `ver=1.3`이 없으면 PM2.5 필드가 없다.

## Review Focus

1. 공공 API가 빈 목록을 반환할 때(출근 시간대에 도착 예정 버스가 없음) → "도착 정보 없음"으로 표시, 예외 없음
2. 존재하지 않는 표지판 번호, 또는 후보 범위 밖의 선택 번호 → 저장하지 않고 안내 메시지
3. 알림 시간에 잘못된 형식 입력(`25:00`, `8시`) → 저장하지 않고 형식 안내
4. 인증키가 Encoding/Decoding 중 잘못된 형태일 때 → 인증 오류를 로그에 구분해 남기고 해당 항목만 `조회 실패`
5. 텔레그램 메시지 발송 실패 → 로그 남기고 다음 스케줄에 영향 없음

---

### Task 1: 프로젝트 스캐폴드와 설정

**Files:**
- Create: `build.gradle`, `settings.gradle`
- Create: `src/main/java/com/example/commutebot/CommuteBotApplication.java`
- Create: `src/main/java/com/example/commutebot/config/BotProperties.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-local.yml.example`
- Test: `src/test/java/com/example/commutebot/config/BotPropertiesTest.java`

**Interfaces:**
- Produces: `BotProperties` — `String telegramToken()`, `String publicApiKey()`(공공데이터포털 키: 날씨, 미세먼지, 버스), `String seoulOpenApiKey()`(서울 열린데이터광장 키: 지하철 전용), `Location location()` (위도 `double lat`, 경도 `double lon`, 에어코리아 측정소명 `String airStation`). 바인딩 prefix는 `bot`.

- [ ] **Step 1: 설정 바인딩 테스트 작성**

`BotPropertiesTest`에서 `bot.telegram-token`, `bot.public-api-key`, `bot.location.lat/lon/air-station` 값이 `BotProperties`에 바인딩되는지 확인한다.

- [ ] **Step 2: 테스트 실행, 실패 확인**

Run: `./gradlew test --tests "*BotPropertiesTest"`
Expected: FAIL (클래스 없음)

- [ ] **Step 3: Gradle 설정, 애플리케이션 클래스, `BotProperties`(`@ConfigurationProperties`) 작성**

`application.yml`에는 DB 경로(`jdbc:sqlite:commute.db`)와 기본 설정만 두고, 키는 `application-local.yml.example`로 안내한다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*BotPropertiesTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: scaffold project and bot configuration"
```

---

### Task 2: 사용자 설정 저장소 (정류장, 역, 알림 시간)

**Files:**
- Create: `src/main/java/com/example/commutebot/store/BusStop.java` (JPA 엔티티)
- Create: `src/main/java/com/example/commutebot/store/SubwayStation.java` (JPA 엔티티)
- Create: `src/main/java/com/example/commutebot/store/AlertSetting.java` (JPA 엔티티, 단일 행)
- Create: `src/main/java/com/example/commutebot/store/UserSettingsService.java`
- Test: `src/test/java/com/example/commutebot/store/UserSettingsServiceTest.java`

**Interfaces:**
- Produces:
  - `UserSettingsService`
    - `void setRepresentativeStop(String stopNumber, String stopName, List<String> routes)` — 대표 정류장 덮어쓰기
    - `Optional<BusStop> representativeStop()`
    - `void addTransferStop(String stopNumber, String stopName, List<String> routes)`
    - `void removeTransferStop(String stopNumber)`
    - `List<BusStop> transferStops()`
    - `void addStation(String name, String line)` — 같은 (name, line) 중복 저장 안 함
    - `void removeStation(String name, String line)`
    - `List<SubwayStation> stations()`
    - `LocalTime alertTime()` — 저장값 없으면 `08:00`
    - `void setAlertTime(LocalTime time)`

- [ ] **Step 1: 실패하는 테스트 작성**

테스트 항목:
- 대표 정류장을 두 번 지정하면 마지막 값만 남는다.
- 같은 (역 이름, 호선)을 두 번 추가해도 한 건만 남는다.
- 알림 시간 설정 전에는 `08:00`을 반환한다.

- [ ] **Step 2: 테스트 실행, 실패 확인**

Run: `./gradlew test --tests "*UserSettingsServiceTest"`
Expected: FAIL

- [ ] **Step 3: 엔티티, 리포지토리, `UserSettingsService` 구현**

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*UserSettingsServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: persist stops, stations, and alert time"
```

---

### Task 3: 날씨 Provider (기상청 단기예보)

**Files:**
- Create: `src/main/java/com/example/commutebot/weather/WeatherProvider.java`
- Create: `src/main/java/com/example/commutebot/weather/KmaWeatherClient.java`
- Create: `src/main/java/com/example/commutebot/weather/GridConverter.java`
- Create: `src/main/java/com/example/commutebot/weather/WeatherSummary.java` (record)
- Test: `src/test/java/com/example/commutebot/weather/GridConverterTest.java`
- Test: `src/test/java/com/example/commutebot/weather/KmaWeatherClientTest.java`

**Interfaces:**
- Produces:
  - `record WeatherSummary(String text)` — 현재 기온·습도·강수 상태(초단기실황), 오늘 최저·최고 기온과 출근 시간대 강수 확률(단기예보)을 한 줄로 요약한 문자열
  - 데이터 소스 규칙: 최저 기온(`TMN`)은 base 0200 단기예보의 `TMN` 항목, 최고 기온(`TMX`)은 가장 최근 발표 시각의 단기예보 `TMX` 항목에서 읽는다. 강수 확률(`POP`)과 하늘 상태(`SKY`)는 출근 시간대(08~09시) 슬롯에서 읽는다.
  - `PCP`, `SNO`의 `강수없음`, `적설없음`은 0으로 처리한다 (숫자 파싱 금지)
  - `interface WeatherProvider { WeatherSummary today(); }` — 실패 시 `WeatherException` 발생
  - `GridConverter.toGrid(double lat, double lon)` → `int[] {nx, ny}` (기상청 격자 변환)

- [ ] **Step 1: `GridConverterTest` 작성**

서울시청 좌표(`37.5665, 126.9780`)를 변환했을 때 기상청 공식 격자값 `nx=60, ny=127`이 나오는지 확인한다. 응암동 좌표는 사용자가 지도에서 확인한 값을 설정에 넣고, 같은 방식으로 `nx=55, ny=127`이 나오는지 확인한다 (샘플 호출 기준 참고값).

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*GridConverterTest"`
Expected: FAIL

- [ ] **Step 3: `GridConverter` 구현 (기상청 격자 변환 공식)**

- [ ] **Step 4: 통과 확인 후 `KmaWeatherClientTest` 작성**

`RestClient`를 MockRestServiceServer로 대체하고, 초단기실황 픽스처에서 `T1H`(기온)와 `PTY`(강수 상태)를, 단기예보 픽스처에서 `TMN`, `TMX`, 출근 시간대 `POP`을 읽어 `WeatherSummary.text`에 들어가는지 확인한다. `PCP`가 `강수없음`이면 강수량 0으로 처리하는지 확인한다. 빈 응답이면 `WeatherException`을 던지는지 확인한다.

- [ ] **Step 5: `KmaWeatherClient` 구현 후 테스트 통과**

Run: `./gradlew test --tests "*Weather*"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add . && git commit -m "feat: add KMA weather provider"
```

---

### Task 4: 미세먼지 Provider (에어코리아)

**Files:**
- Create: `src/main/java/com/example/commutebot/airquality/AirQualityProvider.java`
- Create: `src/main/java/com/example/commutebot/airquality/AirKoreaClient.java`
- Test: `src/test/resources/fixtures/air-realtime-sample.json` (2026-11-24 10:00 은평구 응답을 그대로 저장: `pm25Value "7"`, `pm10Value "52"` 등, `ver=1.3` 응답)
- Test: `src/test/java/com/example/commutebot/airquality/AirKoreaClientTest.java`

**Interfaces:**
- Produces:
  - `record AirQualitySummary(String text)` — PM10·PM2.5 수치와 등급(1 좋음, 2 보통, 3 나쁨, 4 매우나쁨)을 한 줄로 요약
  - `interface AirQualityProvider { AirQualitySummary now(); }` — 실패 시 `AirQualityException`
- Consumes: `BotProperties.location().airStation()`

**Endpoint:** `https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty?serviceKey={publicApiKey}&stationName={airStation}&dataTerm=DAILY&numOfRows=1&pageNo=1&returnType=json&ver=1.3`
- 응답 `items[0]`에서 읽는 필드: `dataTime`, `pm10Value`, `pm10Grade1h`, `pm25Value`, `pm25Grade1h`, `pm25Value24`, `pm10Value24`, `khaiValue`, `khaiGrade`, `mangName`
- `dataTime`은 `"yyyy-MM-dd HH:mm"`이고 `24:00`이면 다음 날 `00:00`으로 변환한다.
- 측정값이 `-`이거나 빈 값이면 `측정값 없음`으로 표시한다.

- [ ] **Step 1: 실패하는 테스트 작성**

테스트 항목:
- 픽스처 JSON을 파싱하면 `text`에 PM10 `52`(2등급), PM2.5 `7`(1등급)이 들어간다.
- `dataTime`이 `"2026-11-23 24:00"`이면 `LocalDateTime`이 `2026-11-24T00:00`이 된다.
- 측정값이 `-`이면 `측정값 없음`으로 표시한다.
- `items`가 비어 있으면 `AirQualityException`을 던진다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*AirKoreaClientTest"`
Expected: FAIL

- [ ] **Step 3: `AirKoreaClient` 구현**

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*AirKoreaClientTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: add air quality provider"
```

---

### Task 5: 버스 도착 Provider (서울시 버스, 정류장 번호 기준)

**Files:**
- Create: `src/main/java/com/example/commutebot/bus/BusStopLookup.java` (표지판 번호 → 정류소 후보, 노선별 순번과 도착 정보)
- Create: `src/main/java/com/example/commutebot/bus/BusArrivalProvider.java`
- Create: `src/main/java/com/example/commutebot/bus/SeoulBusClient.java` (버스도착정보조회 서비스: `getArrInfoByRouteList` 호출)
- Create: `src/main/java/com/example/commutebot/bus/BusArrival.java` (record)
- Test: `src/test/resources/fixtures/bus-route-list-sample.json`, `src/test/resources/fixtures/bus-stop-lookup-sample.json` (실제 호출 응답을 저장해서 사용)
- Test: `src/test/java/com/example/commutebot/bus/BusStopLookupTest.java`
- Test: `src/test/java/com/example/commutebot/bus/SeoulBusClientTest.java`

**Interfaces:**
- Produces:
  - `record BusStopRef(String stId, String arsId, String stName, List<BusRouteAtStop> routes)`
  - `record BusRouteAtStop(String busRouteId, String routeName, int staOrd)` — `staOrd`는 해당 정류소의 노선별 순번(`ord`)
  - `record BusStopCandidate(String stId, String arsId, String stName, List<BusRouteAtStop> routes)` — `getStationByUid` 응답을 `stId`로 묶은 한 건. 좌표는 `gpsX`(경도), `gpsY`(위도)
  - `interface BusStopLookup { List<BusStopCandidate> findByArsId(String arsId); }` — `getStationByUid(arsId)` 호출. 결과가 없으면 빈 목록, 서로 다른 `stId`가 있으면 여러 후보. 인증 오류는 `BusApiException`
  - 도착 정보는 같은 응답의 `arrmsg1`, `arrmsg2`로 만든다. `getArrInfoByRouteList`는 쓰지 않는다 (2026-11-24 결정)
  - `record BusArrival(String routeName, String firstArrivalText, String secondArrivalText)` — 노선별 첫 번째·두 번째 도착 문구 (`arrmsg1`, `arrmsg2`)
  - `interface BusArrivalProvider { List<BusArrival> arrivalsAt(BusStopRef stop); }` — 경유 노선마다 `getArrInfoByRouteList(stId, busRouteId, ord)`를 호출해 합친다. 응답 코드 `3`(정류소 없음), `4`(노선 없음), `8`(운행 종료)은 빈 결과 또는 안내 문구로 처리한다.
- Consumes: `BotProperties.publicApiKey()`

**Endpoints (가이드 기준):**
- 도착: `http://ws.bus.go.kr/api/rest/arrive/getArrInfoByRoute?serviceKey=...&stId=...&busRouteId=...&ord=...&resultType=json`
- 정류소 조회: `http://ws.bus.go.kr/api/rest/stationinfo/getStationByUid?serviceKey={key}&arsId={표지판번호}&resultType=json`. 응답 `msgBody.itemList`의 각 항목에 `stId`, `stNm`, `arsId`, `busRouteId`, `rtNm`, `staOrd`(노선별 순번 = `ord`), `adirection`(방면), `arrmsg1`, `arrmsg2`, `gpsX`(경도), `gpsY`(위도)가 있다. 헤더 `headerCd "0"`이 정상이다.
- 주의: `firstTm`, `lastTm`은 뒤에 공백이 있으니 trim한다. `repTm`(예: 2021년)은 실시간 시각이 아니므로 쓰지 않는다. `itemCount`는 쓰지 않는다.

- [ ] **Step 0: 샘플 응답 확보 (구현 전 확인)**

표지판 번호 `13221` 조회 응답(`getStationByUid`, 노선 10개)은 확보했다. 이 응답을 `src/test/resources/fixtures/bus-station-by-uid-13221.json`으로 저장한다. 같은 번호에 여러 `stId`가 나오는 실제 샘플은 아직 없으므로, 그 테스트는 합성 JSON으로 만든다. 필드가 없으면 이 태스크를 멈추고 설계 문서를 먼저 고친다.

- [ ] **Step 1: 실패하는 테스트 작성**

`BusStopLookupTest`:
- 픽스처(`arsId=13221`)에서 `stId` `112000138` 후보 1건이 나오고, 노선 10개의 `busRouteId`, `rtNm`, `staOrd`가 맞게 파싱된다 (예: 7017 → 78).
- `stId`가 다른 합성 응답이면 후보 2건이 나온다.
- 결과가 없으면 빈 목록이다.

`SeoulBusClientTest`:
- `getArrInfoByRouteList` 픽스처(`arrmsg1`, `arrmsg2`, `routeType`, `rtNm`)에서 `BusArrival`이 만들어진다.
- 응답 `headerCd`가 `3`이면 빈 결과를 반환한다.
- 인증 오류면 `BusApiException`이 던져지고, 메시지에 "인증키"가 포함된다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*SeoulBusClientTest"`
Expected: FAIL

- [ ] **Step 3: `SeoulBusClient` 구현**

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*SeoulBusClientTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: add Seoul bus arrival provider"
```

---

### Task 6: 지하철 도착 Provider (역 이름과 호선 기준)

**Files:**
- Create: `src/main/resources/subway-stations.csv` (제공된 역 마스터 파일을 CSV로 변환한 것: `SUBWAY_ID,STATN_ID,STATN_NM,호선이름`, 696행)
- Create: `src/main/java/com/example/commutebot/subway/SubwayStationCatalog.java`
- Create: `src/main/java/com/example/commutebot/subway/SubwayArrivalProvider.java`
- Create: `src/main/java/com/example/commutebot/subway/SeoulSubwayClient.java`
- Create: `src/main/java/com/example/commutebot/subway/SubwayArrival.java` (record)
- Test: `src/test/resources/fixtures/subway-realtime-sample.json` (아래 샘플 응답을 그대로 저장)
- Test: `src/test/java/com/example/commutebot/subway/SubwayStationCatalogTest.java`
- Test: `src/test/java/com/example/commutebot/subway/SeoulSubwayClientTest.java`

**Interfaces:**
- Produces:
  - `SubwayStationCatalog`
    - `boolean exists(String statnName, String lineName)` — 역명은 `STATN_NM` 값과 정확히 일치해야 한다 (예: `새절(신사)`)
    - `Optional<Integer> subwayIdOf(String lineName)` — `호선이름` → `SUBWAY_ID` (예: `6호선` → `1006`)
    - `List<String> search(String keyword)` — `/역추가` 입력 보조용 후보 목록 (부분 일치)
  - `record SubwayArrival(String line, String direction, int minutesLeft, String destination, String arrivalMessage, int arrivalCode)` — `line`은 `subwayId` 기준 호선명, `direction`은 `updnLine`, `minutesLeft`는 `barvlDt / 60`(`barvlDt`가 0이면 `-1`), `destination`은 `bstatnNm`, `arrivalMessage`는 `arvlMsg2`, `arrivalCode`는 `arvlCd`. 표시 규칙: `minutesLeft >= 0`이면 `N분 후 (arrivalMessage의 전역명)`, `-1`이면 `arrivalMessage`를 그대로 출력
  - `interface SubwayArrivalProvider { List<SubwayArrival> arrivalsAt(String statnName, String lineName); }` — 서울 API 응답 중 `subwayId`가 해당 호선인 것만 반환
- Consumes: `UserSettingsService.stations()` 의 `SubwayStation(name, line)`

**Endpoint:** `http://swopenAPI.seoul.go.kr/api/subway/{seoulOpenApiKey}/json/realtimeStationArrival/0/5/{역명}`
- 안내 문서의 URL은 `xml`이지만 샘플 응답은 JSON이므로 `json`을 쓴다.
- 역명은 URL 인코딩해서 넣는다.
- 성공 판정은 `errorMessage.code == "INFO-000"`이다. 목록이 비면 빈 결과로 처리한다.
- 로그에는 인증키를 마스킹해서 남긴다.

- [ ] **Step 1: 실패하는 테스트 작성**

테스트 항목:
- `SubwayStationCatalogTest`: `exists("새절(신사)", "6호선")`은 true, `exists("새절", "6호선")`은 false. `subwayIdOf("6호선")`은 `1006`.
- `SeoulSubwayClientTest`: 픽스처 JSON(새절(신사) 응답 4건)을 파싱하면 `SubwayArrival` 4개가 나오고, `barvlDt "240"`은 `minutesLeft == 4`, `updnLine "상행"`이 그대로 `direction`에 들어간다.
- 같은 역의 응답에 다른 호선(`subwayId`가 다른 항목)이 섞여 있으면 요청한 호선만 남는다.
- `barvlDt`가 `"0"`이면 `minutesLeft == -1`이고, 표시 문자열이 `arvlMsg2` 그대로다 (소요산 샘플: `[7]번째 전역 (양주)`).
- 1호선 경기 구간 역(소요산)도 등록 가능하고, 픽스처 JSON으로 파싱이 통과한다.
- 응답 목록이 비어 있으면 빈 목록이다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*SeoulSubwayClientTest"`
Expected: FAIL

- [ ] **Step 3: `SeoulSubwayClient` 구현**

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*SeoulSubwayClientTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: add Seoul subway arrival provider"
```

---

### Task 7: 알림 메시지 조립기

**Files:**
- Create: `src/main/java/com/example/commutebot/alert/AlertComposer.java`
- Create: `src/main/java/com/example/commutebot/alert/DirectionGrouper.java`
- Test: `src/test/java/com/example/commutebot/alert/AlertComposerTest.java`

**Interfaces:**
- Produces:
  - `String compose(WeatherSummary weather, AirQualitySummary air, Optional<BusStop> representative, List<BusStop> transfers, List<SubwayStation> stations, Map<String, List<BusArrival>> busByStop, Map<String, List<SubwayArrival>> subwayByStation)` — 최종 텔레그램 메시지
  - `DirectionGrouper.topTwoPerDirection(List<SubwayArrival>)` → `Map<String, List<SubwayArrival>>` (방향별 최대 2개)
- Consumes: Task 2, 3, 4, 5, 6의 record 타입

- [ ] **Step 1: 실패하는 테스트 작성**

테스트 항목:
- 대표 정류장이 없으면 "대표 정류장을 설정해 주세요" 문구가 포함된다.
- 지하철 방향별로 3대가 있으면 2대만 남는다.
- 버스 조회 결과가 `null`(조회 실패)이면 "조회 실패"로 표시하고 나머지 항목은 그대로 출력된다.
- 도착 정보가 없으면 "도착 정보 없음"을 표시한다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*AlertComposerTest"`
Expected: FAIL

- [ ] **Step 3: `DirectionGrouper`, `AlertComposer` 구현**

메시지 섹션 순서는 날씨·미세먼지, 대표 정류장 버스, 환승 정류장 버스, 지하철 순이다.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*AlertComposerTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: compose alert message with section ordering"
```

---

### Task 8: 알림 수집 서비스 (Provider 호출 + 실패 격리)

**Files:**
- Create: `src/main/java/com/example/commutebot/alert/AlertService.java`
- Test: `src/test/java/com/example/commutebot/alert/AlertServiceTest.java`

**Interfaces:**
- Produces: `String buildAlertMessage()` — 모든 Provider를 호출하고 `AlertComposer.compose`에 넘겨 최종 문자열을 반환. 한 Provider가 예외를 던져도 나머지는 계속 호출한다.
- Consumes: Task 2~7 전부. 캐시는 날씨·미세먼지에 대해 같은 날짜면 재사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

Mockito로 Provider를 대체한다.
- 날씨 Provider가 예외를 던져도 버스 결과는 메시지에 들어 있다.
- 같은 날 `buildAlertMessage()`를 두 번 호출하면 날씨 Provider는 한 번만 호출된다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*AlertServiceTest"`
Expected: FAIL

- [ ] **Step 3: `AlertService` 구현 (날짜 단위 캐시 포함)**

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*AlertServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: collect provider results with failure isolation and daily cache"
```

---

### Task 8W: 웹 화면 (1차 최종)

**Files:**
- Modify: `build.gradle` (`spring-boot-starter-web`, `spring-boot-starter-thymeleaf` 추가)
- Create: `src/main/java/com/example/commutebot/web/DashboardController.java`
- Create: `src/main/java/com/example/commutebot/web/StopRegistrationController.java`
- Create: `src/main/resources/templates/dashboard.html`
- Test: `src/test/java/com/example/commutebot/web/DashboardControllerTest.java`

**Interfaces:**
- Produces:
  - `GET /` — 날씨, 미세먼지, 대표 정류장 버스, 환승 정류장 버스, 지하철 도착을 섹션별로 보여준다. 한 Provider가 실패하면 그 섹션만 "조회 실패"로 표시한다.
  - `POST /stops/bus` (파라미터 `arsId`, 대표 여부 `representative`) — 표지판 번호로 `findByArsId` 호출. 후보가 1건이면 바로 저장하고 `/`로 돌아간다. 후보가 여러 건이면 후보 선택 화면(`GET /stops/bus/choose`)을 보여준다.
  - `POST /stops/bus/choose` (파라미터 `stId`) — 선택한 후보를 저장한다.
  - `POST /stops/subway` (파라미터 `name`, `line`) — `SubwayStationCatalog.exists`를 통과해야 저장한다.
  - `POST /stops/delete` — 등록한 정류장·역 삭제.
- Consumes: Task 2 `UserSettingsService`, Task 3~6 Provider, Task 5 `BusStopLookup.findByArsId`

- [ ] **Step 1: 실패하는 테스트 작성**

`DashboardControllerTest` (MockMvc, Provider는 Mockito 대체):
- `GET /` 응답이 200이고, 본문에 "날씨", "미세먼지", "버스", "지하철" 섹션 제목이 있다.
- 미세먼지 Provider가 예외를 던지면 본문에 "조회 실패"가 있고 날씨 섹션은 그대로 나온다.
- `POST /stops/bus`에 `arsId=13221`을 보내면 `UserSettingsService.setRepresentativeStop`이 `stId=112000138`로 호출된다.
- `POST /stops/subway`에 존재하지 않는 역명을 보내면 저장되지 않고 안내가 표시된다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*Dashboard*"`
Expected: FAIL

- [ ] **Step 3: 컨트롤러와 템플릿 구현**

화면에서 날씨 요약 값(현재 기온, 최저·최고, 강수 확률)과 미세먼지 값(PM10, PM2.5 수치와 등급)은 각각 한 줄로 보여준다. 버스 섹션은 노선별 `rtNm`, `arrmsg1`, `arrmsg2`를 표로 보여준다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*Dashboard*"`
Expected: PASS

- [ ] **Step 5: 실행 확인**

Run: `./gradlew bootRun --args='--spring.profiles.active=local'` 후 `http://localhost:8080` 접속.
Expected: 날씨, 미세먼지, 등록한 정류장·역 도착 정보가 보인다.

- [ ] **Step 6: 커밋**

```bash
git add . && git commit -m "feat: add web dashboard for weather, air, bus, and subway"
```

---

### Task 9 (2차 보류): 텔레그램 명령 처리 (롱폴링)

**Files:**
- Create: `src/main/java/com/example/commutebot/telegram/TelegramClient.java`
- Create: `src/main/java/com/example/commutebot/telegram/CommandParser.java`
- Create: `src/main/java/com/example/commutebot/telegram/CommandHandler.java`
- Create: `src/main/java/com/example/commutebot/telegram/TelegramPoller.java`
- Test: `src/test/java/com/example/commutebot/telegram/CommandParserTest.java`
- Test: `src/test/java/com/example/commutebot/telegram/CommandHandlerTest.java`

**Interfaces:**
- Produces:
  - `record Command(String name, List<String> args)` — `CommandParser.parse(String text)`, 명령이 아니면 `Optional.empty()`
  - `CommandHandler.handle(Command cmd)` → `String` 응답 문자열
    - `/대표정류장`, `/환승정류장추가`, `/환승정류장삭제`, `/역추가`, `/역삭제`, `/정류장목록`, `/역목록`, `/알림시간`, `/지금`
  - `TelegramClient.sendMessage(String text)` — 실패 시 로그만 남기고 예외를 전파하지 않는다.
- 정류장 등록 흐름: `/대표정류장 {표지판번호}` → `findByArsId` 결과가 1건이면 바로 저장, 여러 건이면 번호, 정류장 이름, 방면과 함께 후보를 보내고 사용자가 번호를 보내면 저장. 선택 대기 상태는 chat ID별로 메모리 Map에 보관한다 (단일 사용자이므로 충분하다).
- Consumes: Task 2 `UserSettingsService`, Task 5 `BusStopLookup.findByArsId`, Task 8 `AlertService.buildAlertMessage`

- [ ] **Step 1: `CommandParserTest` 작성 후 실패 확인**

테스트 항목: `/알림시간 08:30`은 name `알림시간`, args `[08:30]`. 일반 대화 문장은 빈 결과.

Run: `./gradlew test --tests "*CommandParserTest"`
Expected: FAIL

- [ ] **Step 2: `CommandParser` 구현 후 통과 확인**

Run: `./gradlew test --tests "*CommandParserTest"`
Expected: PASS

- [ ] **Step 3: `CommandHandlerTest` 작성 후 실패 확인**

테스트 항목:
- `/알림시간 25:00`은 저장하지 않고 형식 안내 문자열을 반환한다.
- `/대표정류장 99999`는 `findByArsId`가 빈 목록을 줄 때 "존재하지 않는 표지판 번호" 안내를 반환하고 저장하지 않는다.
- `findByArsId`가 `stId`가 다른 후보 2건을 주면 번호, 이름, 방면과 함께 보여준다. 이어서 `2`를 보내면 두 번째 후보가 저장된다. 목록 범위 밖의 번호는 저장하지 않고 다시 고르게 한다.
- `findByArsId`가 1건이면 목록 없이 바로 저장한다.
- `/역추가 옥수`처럼 호선이 없으면 "호선을 함께 입력해 주세요" 안내를 반환한다.
- `/역추가 새절 6호선`처럼 역명이 `SubwayStationCatalog`에 없으면 저장하지 않고, `search("새절")` 결과인 `새절(신사)`를 후보로 안내한다.
- `/역추가 새절(신사) 6호선`처럼 정확한 역명과 호선이 맞으면 저장한다.

Run: `./gradlew test --tests "*CommandHandlerTest"`
Expected: FAIL

- [ ] **Step 4: `CommandHandler`, `TelegramClient`, `TelegramPoller` 구현 후 통과 확인**

`TelegramPoller`는 `@Scheduled(fixedDelay = 1000)`으로 `getUpdates`를 호출하고, 마지막 `update_id + 1`을 다음 offset으로 사용한다. 요청하는 사용자는 `bot.owner-chat-id` 한 명으로 제한한다.

Run: `./gradlew test --tests "*Command*"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: handle telegram commands via long polling"
```

---

### Task 10 (2차 보류): 알림 스케줄러 (평일, 동적 시간)

**Files:**
- Create: `src/main/java/com/example/commutebot/schedule/AlertScheduler.java`
- Create: `src/main/java/com/example/commutebot/config/SchedulerConfig.java`
- Test: `src/test/java/com/example/commutebot/schedule/AlertSchedulerTest.java`

**Interfaces:**
- Produces:
  - `AlertScheduler.start()` — 앱 시작 시 DB의 알림 시간으로 등록
  - `AlertScheduler.reschedule(LocalTime time)` — 기존 작업 취소 후 새 시간으로 등록. `CommandHandler`의 `/알림시간` 처리에서 호출
  - 실행 조건: 평일(`DayOfWeek` MONDAY~FRIDAY)일 때만 `AlertService.buildAlertMessage()` 결과를 `TelegramClient.sendMessage()`로 보낸다.
- Consumes: Task 2 `UserSettingsService.alertTime()`, Task 8, Task 9
- Note: 시간은 `Asia/Seoul` 시간대를 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

테스트 항목:
- `reschedule(LocalTime.of(7, 30))` 호출 후 등록된 cron이 `0 30 7 * * MON-FRI`이다.
- 토요일 실행 시 `sendMessage`가 호출되지 않는다.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "*AlertSchedulerTest"`
Expected: FAIL

- [ ] **Step 3: `SchedulerConfig`(`TaskScheduler` 빈), `AlertScheduler` 구현**

`CommandHandler`의 `/알림시간` 처리에서 `UserSettingsService.setAlertTime` 후 `AlertScheduler.reschedule`을 호출하도록 연결한다.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "*AlertSchedulerTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add . && git commit -m "feat: schedule weekday alerts with runtime-changeable time"
```

---

### Task 11 (2차 보류): 통합 확인과 실행 안내

**Files:**
- Create: `src/test/java/com/example/commutebot/CommuteBotIntegrationTest.java`
- Create: `README.md`

**Interfaces:**
- Consumes: 전체 모듈. 외부 API는 MockRestServiceServer로 대체한다.

- [ ] **Step 1: 통합 테스트 작성 후 실행**

테스트 항목:
- 정류장 등록 → 역 등록 → `/지금` 응답에 날씨, 미세먼지, 버스, 지하철 섹션이 모두 있다.
- 외부 API 하나를 실패시켜도 나머지 섹션은 정상 출력된다.

Run: `./gradlew test`
Expected: 전체 PASS

- [ ] **Step 2: `README.md` 작성**

포함 내용: 공공데이터포털 활용 신청과 승인 대기 안내, `application-local.yml` 작성 방법, 텔레그램 봇 생성 방법, 실행 명령(`./gradlew bootRun --args='--spring.profiles.active=local'`), 명령어 목록.

- [ ] **Step 3: 로컬 실행 확인**

Run: `./gradlew bootRun --args='--spring.profiles.active=local'`
Expected: 로그에 "Telegram poller started"와 "Alert scheduled at 08:00"이 출력되고, 봇에 `/지금`을 보내면 알림 메시지가 도착한다.

- [ ] **Step 4: 커밋**

```bash
git add . && git commit -m "docs: add README and integration test"
```

---

## Self-Review 결과

- 설계 문서의 기능(날씨, 미세먼지, 대표·환승 정류장, 지하철, 명령어, 알림 시간, `/지금`, 실패 격리)은 Task 3~11에 모두 대응된다.
- 명령어와 Provider 시그니처는 Task 2, 5, 6, 7, 9 사이에서 이름이 일치하는지 확인했다.
- Review Focus 다섯 항목은 Task 5, 4, 9, 3, 8에 테스트로 연결되어 있다.
