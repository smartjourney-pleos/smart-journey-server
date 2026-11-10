# Pleos Connect SDK 분석 — 서버 관점

> 작성 2026-11-10 · 기준: document.pleos.ai SDK 릴리즈 노트(2026-10-19) — Vehicle 2.3.2 / NaviHelper 3.0.5 / TextToSpeech 3.3.1 / SpeechToText 3.3.1 / FusedLocation 1.1.3
> 읽은 범위: SDK 사용 가이드 5종(가이드 + API Reference 830여 쪽), Gleo Interface 구현 가이드 6쪽, 앱 계획·개발·테스트·등록·심사 가이드, 릴리즈 노트

서버는 SDK를 직접 호출하지 않는다. 대신 **차량 앱이 SDK에서 받은 값이 어떤 모양·단위로 서버에 오는지**, 그리고 **심사 기준이 서버에 무엇을 요구하는지**가 서버 설계를 정한다. 이 문서는 그 두 가지만 정리한다.

---

## 1. 한눈에 보기

| SDK | 우리가 쓰는 것 | 서버에 미치는 영향 |
| --- | --- | --- |
| **NaviHelper 3.0.5** | 경로 입력, 남은 시간, 도착 이벤트 | ETA는 **초 단위** → 앱이 분으로 올림해서 `PUT /waitings/{id}/eta`. 매장 좌표·POI를 서버가 내려줘야 경로 입력 가능 |
| **Vehicle 2.3.2** | Seat 점유·벨트, Door 위치·잠금 | 하차 확인 근거(시각·점유 수)만 서버로. VIN 같은 차량 식별값은 절대 안 받음 |
| **Gleo Interface** | 음성 함수 3개 | **7초 타임아웃** → 음성 경로에서 부르는 API는 p95 1초 이내 |
| TextToSpeech 3.3.1 | (선택) 음성 안내 | 서버 영향 없음. ON_DEVICE 모드는 앱 등록 불필요 |
| SpeechToText 3.3.1 | 안 씀 | Gleo가 음성 명령을 처리하므로 불필요 |
| FusedLocation 1.1.3 | 안 씀 (보조 후보) | 좌표를 쓰더라도 **차 안에서만** — 서버로 보내지 않음 (정책 4) |
| AAOS CarUxRestrictions | 정차/주행 판별 | 서버 영향 없음 |

---

## 2. NaviHelper 3.0.5 — 서버가 알아야 할 것

### 2.1 API (공식 API Reference 전체 목록)

| 분류 | 메서드 | 권한 |
| --- | --- | --- |
| 경로 | `requestRoute(RouteInfo)`, `requestRouteWithWaypoints(RouteWithWaypointsRequest)`, `cancelRoute()`, `requestReRoute()`, `changeRouteOption(RouteOption)` | `pleos.car.permission.NAVI_ROUTE_SEARCH` |
| 경유지 | `addWaypoint(RequestWaypointInfo)`, `removeWaypoint(WaypointIndex)` — FIRST · SECOND · ALL | `NAVI_ROUTE_SEARCH` |
| 조회 | `getDestinationInfo()`, `getWaypointInfo()`, `getRouteStateInfo()`, `getCurrentLocationInfo()`, `getTBTInfo()` … | `pleos.car.permission.NAVI_ROUTE` |
| 미리보기 | **`requestTrip(TripRequest)`** → `onTripInfo(TripInfo)` | `NAVI_ROUTE` |
| 화면 | `requestNavigationScreen(...)` | `NAVI_CUSTOM_MAP` |
| 생명주기 | `initialize(onConnected, onFailed)`, `addListener`, `removeListener`, `release` | – |

조회 API는 값을 돌려주지 않고 **콜백으로만** 준다 (`getWaypointInfo()` → `onWayPointInfo(List<WaypointInfo>)`).

### 2.2 남은 시간(ETA) — 단위와 출처 ★

| 데이터 | 필드 | 단위 | 받는 법 |
| --- | --- | --- | --- |
| `WaypointInfo` | `remainTime`, `remainDistance` | **초**, m | `getWaypointInfo()` → `onWayPointInfo` |
| `DestinationInfo` | `remainTime`, `remainDistance` | **초**, m | `getDestinationInfo()` → `onDestinationInfo` |
| `DrivingInfo` | `destination.duration`, `waypoints[].duration` | 초 | `onDrivingInfo` (주행 중 통지) |
| `TripInfo` | `eta`, `distance` | **초**, m | `requestTrip(TripRequest)` → `onTripInfo` |
| `RouteStartInfo` | `duration`, `distance` | 초, m | `onRouteStarted` |
| `WaypointArrivedInfo` | `index`, `name`, `duration`, `distance` | 초, m | `onWayPointArrived` |

→ **경유지별 남은 시간이 직접 나온다.** 명세서 5.2의 "경유지별 값이 없으면 매장을 목적지로 두는 보정"은 필요 없을 가능성이 높다 (에뮬레이터 확인).
→ 서버 API는 **분(int)** 으로 받는다 (`etaMin`). 앱이 `ceil(sec / 60)`.

### 2.3 경로 입력에 서버가 내려줘야 하는 값

`RouteInfo(latitude, longitude, poiName, poiId, address, poiSubId, routeOption)` / `RequestWaypointInfo(latitude, longitude, poiName, waypointIndex, poiId, address, poiSubId)`
→ `GET /api/v1/stores` 응답에 `latitude, longitude, poiId, poiSubId, address` 를 그대로 넣었다.
→ 3.0.5부터 **좌표 인자 순서가 (위도, 경도)** 로 바뀌었다 (이전 버전 샘플 주의).

### 2.4 명세서 v3.1 대비 바로잡을 것

| 명세서 v3.1 | 공식 문서 (3.0.5) | 영향 |
| --- | --- | --- |
| "`requestRouteWithWaypoints`, `requestTrip`은 문서 API 목록에 없다" | **둘 다 있다.** 3.0.5 신규 API (릴리즈 노트) | 코스 일괄 입력은 `requestRouteWithWaypoints` 한 번이면 됨. `requestRoute` → `onRoutingCompleted` → `addWaypoint` ×2 는 폴백 |
| ETA "요청 함수·필드명 에뮬레이터 확인" | `onTripInfo(TripInfo.eta 초)`, `WaypointInfo.remainTime 초` | 확인할 건 "실제로 값이 오는지"만 |
| `onRouteEnded` | deprecated → `onRoutingCompleted` | – |
| 권한 문자열 미확정 | `pleos.car.permission.NAVI_ROUTE` / `NAVI_ROUTE_SEARCH` / `NAVI_CUSTOM_MAP` | 매니페스트 확정 |

### 2.5 `requestTrip` 으로 좌표 없이 코스 최적화하기 ★

명세서의 `POST /routes/optimize {stops[], start}` 는 **차량 현재 위치(start)를 서버로 보낸다** → 정책 4 위반.
대안: 앱이 후보 매장마다 `requestTrip(TripRequest(매장 좌표))` → `onTripInfo.eta` 로 **"지금 위치 → 매장" 시간(분)만** 구해서 보낸다. 매장 ↔ 매장 시간은 서버가 매장 좌표로 계산한다.
→ `POST /api/v1/routes/optimize { storeIds[], firstLegMin{storeId: 분} }` 로 바꿨다. (`requestTrip` 이 경로를 바꾸지 않고 미리보기만 하는지는 에뮬레이터 확인 필요)

---

## 3. Vehicle SDK 2.3.2

- Maven: `ai.pleos.playground:Vehicle:2.3.2` (Nexus `https://nexus-playground.pleos.ai/repository/maven-releases/`). getting-started 페이지는 아직 2.3.1 표기 — 릴리즈 노트 기준 2.3.2. **제공받은 AAR 재압축 없이 Gradle로 받을 수 있을 가능성** → 앱 담당 확인.
- 권한: `CAR_SEATS`(Seat), `CAR_DOORS`(Door). 제어는 `CONTROL_CAR_*`.
- 에러: `VehicleException.errorCode` — `ERROR_NOT_SUPPORTED_PROPERTY/AREA`, `ERROR_SECURITY_PERMISSION`, `ERROR_SERVICE_CONNECTION_FAILURE`, `ERROR_DRIVING_RESTRICTED` …
- 2.3.2 Breaking: `SeatArea`·`DoorArea` 등이 **내용 기반 equals/hashCode** 로 바뀜 → 영역을 Map 키로 쓰는 `PleosVehicleGateway.kt` 는 오히려 안전해짐.

### 하차 확인에 쓸 수 있는 신호 (서버 `arrival_check` 근거 컬럼)

| 신호 | API | Virtual Console 조작 | 서버 컬럼 |
| --- | --- | --- | --- |
| 좌석 점유 | `Seat.registerSeatOccupancy` | ✅ Seat Occupancy (운전석·동승석·뒷좌석 좌우) | `occupancy_before/after` |
| 도어 위치 | `Door.registerDoorPosition` | ✅ Door Position | `door_opened_at` |
| **도어 잠금 (신규 제안)** | `Door.registerDoorLockState` | ✅ Door Lock | `door_locked_at` — "전부 잠김 = 운전자도 내림" → M = N 보조 근거 |
| 안전벨트 | `Seat.registerSeatBeltBuckledState` | ⚠️ 좌석별 카드가 목록에 없음 (Telltales 경고등만) | `belt_released_at` — 에뮬레이터 시연 불가 가능성 |

- `CarInfo.getVINInfo()` 가 있지만 **서버는 VIN을 받지 않는다.** 기기 식별은 동의 시 발급하는 임의 토큰뿐 (보안 가이드: "차량 ID 가명화 정책 수립").

---

## 4. Gleo Interface — 서버 응답 속도 기준

| 항목 | 내용 | 서버 영향 |
| --- | --- | --- |
| 함수 수 | `actionCalls` 최대 3개 | `OPTIMIZE_DAILY_ROUTE`, `REGISTER_GOLDEN_TIME_WAITING`, `CHECK_WAITING_STATUS` |
| 함수 이름 | **대문자 + 밑줄** 필수 | 명세서의 camelCase(`optimizeDailyRoute`) 수정 필요 |
| 설명 | 영어, 예시 금지, 1~2문장 | – |
| 호출 | Intent action = 함수 이름, `ACTION_PARAM` extra(JSON). `component: "service"` 면 broadcast → 백그라운드 엔진이 받기 좋음 | – |
| 응답 | broadcast `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` `{functionName, status: success/asking/fail, message(영어), data, mode}` | – |
| **타임아웃** | **7초 후 Gleo가 취소** | 음성 경로 API(`POST /waitings`, `GET /journeys/current`) **p95 1초**, 앱 HTTP 타임아웃 3초 |
| 등록 | `assets/schema.json` → 검수 승인 → 자동 활성화 | 승인 전엔 화면 승인으로 시연 |

`CHECK_WAITING_STATUS` 의 `data` 재료가 `GET /api/v1/journeys/current` 응답(`stops[].waiting.aheadTeams, estimatedEntryAt`)이다.

---

## 5. TextToSpeech · SpeechToText · FusedLocation

- **TTS 3.3.1 문서는 공개됨**: `TextToSpeech(context, Mode.ON_DEVICE)` 는 네트워크·앱 등록 없이 동작, 권한 `pleos.car.permission.TTS_SERVICE`, locale `ko-KR` 지원. 단 Playground "SDK 사용 신청" 페이지에는 TTS·STT·FusedLocation 이 아직 **준비 중**. → 승인 카드 음성 안내를 ON_DEVICE TTS로 할 수 있는지 앱 담당이 에뮬레이터에서 확인. (서버 영향 없음)
- STT: Gleo가 음성 명령을 처리하므로 쓰지 않는다.
- FusedLocation: `addProximityAlert` 로 "매장 반경 진입"을 도착 보조 신호로 쓸 수는 있다. 쓰더라도 **좌표는 차 안에서만** 쓰고 서버엔 결과(도착 시각)만.

---

## 6. 심사·정책이 서버에 요구하는 것

| 출처 | 요구 | 서버 반영 |
| --- | --- | --- |
| 리뷰 가이드 — 개인정보 | 고지에 **수집 항목·목적·보유 기간·거부 권리** 필수, 명시적 동의 버튼만 동의로 간주 | `GET /api/v1/consent/notice` 에 모두 포함, 버전 불일치 시 동의 거부 |
| 리뷰 가이드 — 등록정보 | **개인정보 처리방침** 제출 | `GET /privacy` 정적 페이지 (초안) |
| 앱 보안 가이드 | 토큰을 **URL 경로·쿼리에 넣지 말 것** | REST는 `X-Device-Token`·`Authorization` 헤더, STOMP는 CONNECT 헤더 |
| 앱 보안 가이드 | 민감정보 로그 마스킹 | 토큰·PIN 로그 금지 (Phase 4 점검) |
| 계획 가이드 — 보안 | 차량·사용자 ID 가명화, 주행 행동 데이터 마케팅 사용 금지 | 임의 토큰, KPI는 집계만 |
| 리뷰 가이드 — 성능 | 버튼 반응 2초, 콘텐츠 로드 10초 | 접수 → POS 표시 2초 (인수 조건 P0-3) |
| 리뷰 가이드 — 기능 | 앱 안에 테스트용 더미 데이터 금지 | 데모 매장 시드는 `local`·`test` 프로필에서만, `postgres` 프로필은 로드 안 함 |

---

## 7. 우리 문서끼리 어긋난 것 (팀 공유 필요)

| 항목 | 내용 | 제안 |
| --- | --- | --- |
| Docker | 명세서 1.2 · 10장: 로컬 Postgres·Testcontainers·배포 모두 Docker | **Docker 미사용(팀 협의)** → 로컬 H2(PostgreSQL 모드), 테스트 H2, 배포는 VM에 JDK 21 + PostgreSQL 직접 설치 + systemd |
| 저장소 공개 | README: Private / 명세서 3장: public | public 이면 `docs/SmartJourney/01_제출용/`(팀원 연락처) 절대 커밋 금지 → `.gitignore` 에 넣어 둠 |
| 중계 서버 표기 | `SmartJourney_제출용_기획서.docx`, PRD v2 는 아직 FastAPI | 최신본(기획서 v3.1, 접수양식)은 Spring Boot. 옛 문서는 99_이전버전으로 |
| Gleo 함수 이름 | camelCase | UPPER_SNAKE |
| 코스 최적화 `start` | 현재 위치 전송 | `firstLegMin` 으로 대체 (2.5절) |
| minSdk | 명세서 ≥ 23 | getting-started 는 **API 28 이상** |
