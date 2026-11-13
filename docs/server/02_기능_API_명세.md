# 서버 기능 · API 명세 v1

> 작성 2026-11-10 · 갱신 2026-11-13 (Phase 1~6 서버 구현 반영) · 기준: 개발 명세서 v3.1 6장 + Pleos SDK 분석(01 문서)
> 실제 계약서는 서버 실행 후 **Swagger UI `/swagger-ui.html`** — 이 문서와 다르면 코드가 맞다.

## 0. 원칙

1. **명령은 REST, 상태 변화는 STOMP.** 재연결하면 REST로 현재 상태를 다시 받아 맞춘다 (이벤트 유실 대비).
2. **좌표·궤적·VIN 은 받지도 저장하지도 않는다.** 차량에서 오는 값은 ETA(분)·인원·하차 인원·도착 여부·웨이팅 정보뿐 (정책 4).
3. **동의 없이는 아무것도 안 받는다.** 철회한 기기는 필터에서 403 `CONSENT_REQUIRED`.
4. **사용자 승인 없는 변경은 API가 없다.** 접수·인원 변경·미루기·순서 변경은 모두 차량에서 승인 후 호출 (정책 2).
5. **음성 경로는 빠르게.** Gleo 7초 타임아웃 → 접수·조회 p95 1초.
6. **미확정을 성공으로 보이지 않는다.** 하차 결과는 CONFIRMED / MANUAL / UNCONFIRMED 3가지로만.

## 1. 서버 기능 목록 (백로그 매핑)

| # | 서버 기능 | 백로그 | 상태 |
| --- | --- | --- | --- |
| S1 | 고지·동의·기기 토큰, 철회 시 전송 차단 | P0-6 | ✅ (철회 시 여정 종료·줄 선 웨이팅 취소 — 3-6) |
| S2 | 매장 목록·상세 (좌표·POI 포함), 대기 현황(골든타임 입력값) | P0-2 | ✅ |
| S3 | 여정(코스) 생성·조회·복구·인원 변경·종료 | P0-1, P0-8 | ✅ Phase 1 |
| S4 | 원격 웨이팅 접수 (멱등, 번호표, 순번) | P0-3 | ✅ Phase 1 |
| S5 | 실시간 동기화 (STOMP 3토픽, 커밋 후 전송) | P0-4 | ✅ Phase 1~2 |
| S6 | POS 로그인·대기열 보드·데모 패널 | P0-4 | ✅ Phase 1 |
| S7 | ETA 전달, 도착·하차 확인 | P0-5 | ✅ Phase 2 |
| S8 | 호출(QR)·착석·노쇼 | P0-5 | ✅ Phase 2 |
| S9 | 매장 설정 (팀당 분·도착 여유·수동 대기) | P0-4 | ✅ |
| S10 | 인원 변동 반영 | P1-1 | ✅ Phase 3 |
| S11 | 코스 순서 제안 (카카오) | P1-2 | ✅ Phase 3 (키 넣으면 동작, 없으면 입력 순서) |
| S12 | 음성용 상태 요약 (Gleo 재료) | P1-3 | ✅ `/journeys/current` 재사용 (p95 30ms) |
| S13 | KPI 이벤트 수집 · 일 집계 · 대시보드 | PRD 지표 | ✅ Phase 3·6 |
| S14 | 순번 미루기, 순서 변경 | P2-1 | ✅ Phase 4 |
| S15 | 보유 기간 파기, 처리방침 페이지 | 정책 4 | ✅ Phase 4 (파기 전 KPI 스냅샷) |
| S16 | 상용 웨이팅 플랫폼 어댑터 계층 | Phase 6 | ✅ 인터페이스만 (`ExternalWaitingPlatform`) |

## 2. 인증

| 대상 | 방식 | 발급 |
| --- | --- | --- |
| 차량 앱 | 헤더 `X-Device-Token: <43자>` | `POST /api/v1/devices` (동의 버튼) |
| 매장 POS | 헤더 `Authorization: Bearer <JWT>` (12시간) | `POST /api/v1/pos/login` (매장 + PIN). 5분 안에 5번 틀리면 5분 잠금 (429) |
| 운영 지표 | 헤더 `X-Ops-Token` | 서버 환경변수 `OPS_TOKEN` |
| STOMP | CONNECT 네이티브 헤더에 위와 같은 값 | – |

토큰은 URL에 절대 넣지 않는다 (Pleos 앱 보안 가이드).

## 3. REST API — 차량 앱

| Method | Path | 요청 | 응답 / 비고 | Phase |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/consent/notice` | – | `{version, items[], notCollected[], recipients[], purpose, retentionDays, refusalRight, privacyPolicyPath}` 인증 불필요 | ✅ |
| POST | `/api/v1/devices` | `{consentVersion}` | 201 `{deviceToken, consentVersion, consentedAt}` 인증 불필요. 버전이 최신 아니면 400 | ✅ |
| GET | `/api/v1/devices/me` | – | `{consentActive, reconsentRequired, …}` 철회 상태에서도 가능 | ✅ |
| PUT | `/api/v1/devices/me/consent` | `{consentVersion}` | 재동의 / 고지 버전 갱신 | ✅ |
| DELETE | `/api/v1/devices/me/consent` | – | 철회 → 이후 403 `CONSENT_REQUIRED` | ✅ |
| GET | `/api/v1/stores` | – | `[{id, name, address, latitude, longitude, poiId, poiSubId, openNow, avgStayMin, queue{…}}]` | ✅ |
| GET | `/api/v1/stores/{id}` | – | 위 한 건 | ✅ |
| GET | `/api/v1/stores/{id}/queue-status` | – | `{storeId, aheadTeams, estimatedWaitMin, arrivalBufferMin, manualOverride, calculatedAt}` | ✅ |
| POST | `/api/v1/routes/optimize` | `{storeIds[], firstLegMin{storeId:분}}` | `{orderedStoreIds[], legsMin[], totalTravelMin, optimized, provider}` 현재 위치 좌표는 안 받음 | ✅ |
| POST | `/api/v1/journeys` | `{partySize, partySource, storeIds[]}` | 201 여정. 기기당 ACTIVE 1개 (409), 4곳 이상 400 `TOO_MANY_STOPS` | ✅ |
| GET | `/api/v1/journeys/current` | – | 진행 중 여정 / 없으면 204. 앱 복구·Gleo 브리핑 재료 | ✅ |
| GET | `/api/v1/journeys/{id}` | – | `{journeyId, status, partySize, stops[{seq, storeId, storeName, status, waiting{…}}]}` | ✅ |
| PATCH | `/api/v1/journeys/{id}/party` | `{partySize, partySource}` | 정차 중 인원 수정 | ✅ |
| PUT | `/api/v1/journeys/{id}/stops` | `{storeIds[]}` | 순서 변경 (승인 후) | ✅ |
| POST | `/api/v1/journeys/{id}/end` | – | 남은 REGISTERED 웨이팅은 취소 | ✅ |
| POST | `/api/v1/journeys/{id}/events` | `{type, storeId, attributes, occurredAt}` | 202. KPI 이벤트 | ✅ |
| POST | `/api/v1/waitings` | 헤더 `Idempotency-Key` + `{journeyId, storeId, partySize, approvalMethod}` | 201 `{waitingId, ticketNo, aheadTeams, estimatedWaitMin, estimatedEntryAt, …}` | ✅ |
| GET | `/api/v1/waitings/{id}` | – | 위와 같은 모양 | ✅ |
| PATCH | `/api/v1/waitings/{id}/party-size` | `{partySize}` | 409 `WAITING_ALREADY_CALLED` | ✅ |
| POST | `/api/v1/waitings/{id}/defer` | `{delayMin}` | 409 `DEFER_LIMIT_EXCEEDED` | ✅ |
| PUT | `/api/v1/waitings/{id}/eta` | `{etaMin}` | 2분 이상 바뀔 때 또는 1분 주기 | ✅ |
| POST | `/api/v1/waitings/{id}/arrival` | `{result, alighted, partySize, evidence{…}}` | 하차 확인 | ✅ |
| GET | `/api/v1/waitings/{id}/ticket` | – | `{ticketNo, qrPayload, expiresAt}` CALLED 이후 | ✅ |
| DELETE | `/api/v1/waitings/{id}` | – | 취소 | ✅ |

### 3.0 웨이팅 응답 규칙 (구현 기준)

- **멱등**: 같은 `Idempotency-Key` + 같은 `{journeyId, storeId, partySize}` → 같은 웨이팅의 **현재 상태** (201, waitingId·ticketNo 동일). 바디가 다르면 409 `IDEMPOTENCY_CONFLICT`. `approvalMethod` 는 비교하지 않는다 → 화면·음성 승인이 같은 키로 거의 동시에 와도 하나만. 키 없으면 400 `IDEMPOTENCY_KEY_REQUIRED`. 기록은 24시간 보관.
- **번호표**: 매장 행 잠금 후 오늘(Asia/Seoul) max+1. 동시 8건 테스트에서 중복 없음.
- **예상 대기(줄 선 팀)**: `aheadTeams × minutesPerTeam`. 점주 수동 대기시간이 있으면 `ceil(manualWaitMin × aheadTeams / 줄 선 팀 수)` (수동 값은 "맨 뒤에 새로 서는 팀" 기준이므로). 호출 이후는 0.
- **취소**: REGISTERED·ARRIVED 만. 호출된 뒤엔 409 `WAITING_ALREADY_CALLED` (매장에 직접).
- **여정 자동 종료**: 마지막 남은 매장의 웨이팅이 착석·노쇼·취소되면 여정 ENDED. `POST /end` 는 줄 선 웨이팅을 취소하고 웨이팅 전 매장은 SKIPPED. 호출된 팀은 그대로 둔다.
- **순번 미루기**: 뒤에 줄 선 팀이 없으면 순번·횟수 그대로 (횟수 안 씀). 도착(ARRIVED) 후엔 409.
- **코스 순서 변경**: 기존 매장들의 순열만. 다르면 400.
- **동의 철회**: 진행 중 여정 종료 + 줄 선 웨이팅 취소(POS 에 `WAITING_CANCELLED`, 기록 payload `{"by":"CONSENT_REVOKED"}`). 호출된 팀은 매장 판단.
- **KPI 이벤트**: `attributes` 에 `lat/lng/latitude/longitude/location/route/vin/utterance/transcript…` 키가 있으면 400.

### 3.1 하차 확인 보고 검증 (`ArrivalPolicy`)

| result | alighted | 의미 | POS 배지 |
| --- | --- | --- | --- |
| `CONFIRMED` | 1..N 필수 | 도착 → 정차 → 문 열림 → 점유 감소 → 문 닫힘 5초 안정 → 재확인 일치 | 초록 "M/N명 하차" |
| `MANUAL` | 없음 또는 0..N | 3분 타임아웃·센서 미지원 → "도착했어요" | 회색 "고객 확인" |
| `UNCONFIRMED` | **보내면 400** | 확정 전 시동 꺼짐·앱 종료 | 노랑 "인원 미확인" |

`evidence`: `arrivedAt, doorOpenedAt, doorLockedAt, beltReleasedAt, settledAt, occupancyBefore, occupancyAfter` (모두 선택, 좌표 없음)

## 4. REST API — 매장 POS

| Method | Path | 비고 | Phase |
| --- | --- | --- | --- |
| GET | `/api/v1/pos/stores` | 로그인 화면 매장 목록 (인증 불필요) | ✅ |
| POST | `/api/v1/pos/login` | `{storeId, pin}` → `{accessToken, expiresAt, storeId, storeName}` | ✅ |
| GET | `/api/v1/pos/queue` | `{aheadTeams, estimatedWaitMin, items[{ticketNo, partySize, channel, vehicleVerified, status, etaMin, arrival{result, alighted, partySize}, …}]}` | ✅ |
| GET | `/api/v1/pos/waitings/{id}` | 팀 상세 + 이벤트 기록 | ✅ |
| POST | `/api/v1/pos/waitings/{id}/call` | 차량에 CALLED + QR | ✅ |
| POST | `/api/v1/pos/waitings/{id}/seat` | 착석 | ✅ |
| POST | `/api/v1/pos/waitings/{id}/no-show` | 노쇼 | ✅ |
| GET / PATCH | `/api/v1/pos/settings` | `{minutesPerTeam, arrivalBufferMin, manualWaitMin, clearManualWait}` → QUEUE_UPDATED | ✅ |
| POST | `/api/v1/pos/demo/walk-ins` | `{count, partySize}` 현장 팀 +N | ✅ |
| DELETE | `/api/v1/pos/demo/walk-ins?count=1` | 현장 팀 −N | ✅ |
| POST | `/api/v1/pos/demo/reset` | 오늘 대기열 초기화 (리허설) | ✅ |
| POST | `/api/v1/pos/tickets/verify` | `{qrPayload}` 입장 QR 확인 | ✅ |

- 데모 API는 `smartjourney.demo.enabled=false` 면 403 `DEMO_DISABLED`.
- `demo/reset`: 살아 있는 팀 전부 정리 — 줄 선 팀 CANCELLED, 호출된 팀 NO_SHOW. 번호표는 이어서 발급.
- `tickets/verify`: 서명·만료·매장·번호표 일치 + 상태 CALLED 일 때만 `valid:true`. 착석 처리는 따로.

### 4.1 운영 지표

| Method | Path | 비고 |
| --- | --- | --- |
| GET | `/api/v1/ops/kpi/daily?from&to` | 헤더 `X-Ops-Token`. 기본 최근 7일, 최대 92일. 원본 파기된 날은 `snapshot:true` |
| GET | `/ops/kpi.html` | 대시보드 (토큰 입력) |

지표: 인원 제안 수락률(SENSOR 여정 비율) · 승인 카드 → 접수 · 음성 승인 비율 · 하차 자동 확정률(웨이팅별 마지막 보고) · 착석/노쇼/취소 · 주행 중 차단 횟수.

## 5. STOMP

엔드포인트 `/ws` (순수 WebSocket, SockJS 없음) · heartbeat 10초 · 봉투 `{type, eventId, occurredAt, payload}`

| 구독 토픽 | 누가 | 이벤트 |
| --- | --- | --- |
| `/topic/stores/{storeId}/queue` | 차량(코스 매장), POS | `QUEUE_UPDATED` = queue-status 응답과 같은 모양 |
| `/topic/waitings/{waitingId}` | 그 웨이팅의 기기만 | `WAITING_UPDATED`, `CALLED {qrPayload}` |
| `/topic/pos/{storeId}` | 그 매장 POS만 | `WAITING_CREATED`, `PARTY_SIZE_CHANGED`, `ETA_UPDATED`, `ARRIVAL_REPORTED`, `WAITING_CANCELLED`, `WAITING_CLOSED` |

- 구독 권한은 `SubscriptionAuthorizer` 가 검사. SEND 프레임은 거부 (명령은 REST).
- 서비스는 `RealtimeMessage` 를 이벤트로 발행 → `RealtimeRelay` 가 **트랜잭션 커밋 후** 전송.
- 클라이언트는 `eventId` 로 중복 무시.

| 이벤트 | payload |
| --- | --- |
| `QUEUE_UPDATED` | `GET /stores/{id}/queue-status` 와 같은 모양 (새로 줄 서는 팀 기준) |
| `WAITING_UPDATED` | `GET /waitings/{id}` 와 같은 모양. 매장 대기열이 바뀔 때마다 그 매장에 줄 선 차량 모두에게 |
| `CALLED` | `GET /waitings/{id}/ticket` 과 같은 모양 `{waitingId, storeName, ticketNo, partySize, qrPayload, expiresAt}` |
| `/topic/pos/*` 전부 | POS 보드 카드 한 장 (`/pos/queue` 의 `items[]` 원소). POS 는 waitingId 로 카드 교체·삭제 |

- 데모 패널 +N / −N 은 `QUEUE_UPDATED` 를 **한 번만** 보낸다.

## 6. 에러

모든 에러 `{code, message, fields?}`. 클라이언트는 `code` 로 분기.

| HTTP | code |
| --- | --- |
| 400 | `INVALID_REQUEST`, `PARTY_SIZE_OUT_OF_RANGE`, `TOO_MANY_STOPS`, `ARRIVAL_INVALID`, `IDEMPOTENCY_KEY_REQUIRED` |
| 401 | `UNAUTHORIZED`, `POS_LOGIN_FAILED` |
| 429 | `POS_LOGIN_LOCKED` (PIN 5분 안에 5번 실패 → 5분) |
| 403 | `CONSENT_REQUIRED`, `FORBIDDEN`, `DEMO_DISABLED` |
| 404 | `STORE_NOT_FOUND`, `JOURNEY_NOT_FOUND`, `WAITING_NOT_FOUND`, `NOT_FOUND` |
| 409 | `WAITING_ALREADY_CALLED`, `INVALID_STATE_TRANSITION`, `DEFER_LIMIT_EXCEEDED`, `DUPLICATE_ACTIVE_WAITING`, `ACTIVE_JOURNEY_EXISTS`, `IDEMPOTENCY_CONFLICT`, `CONCURRENT_UPDATE` |
| 501 | `NOT_IMPLEMENTED` — 현재 해당 API 없음 (모두 구현) |

## 7. 상태 머신

```
[차량 앱 안에서만]  PLANNED ─골든타임─▶ PROMPTED ─승인─▶ (POST /waitings)
                                       └─무응답 60초/거절─▶ PLANNED

[서버]  REGISTERED ─▶ ARRIVED ─▶ CALLED ─▶ SEATED
            │            │         └──▶ NO_SHOW
            ├────────────────────▶ CALLED      (도착 전에 점주가 호출)
            └─▶ CANCELLED ◀── ARRIVED
```

- 인원 변경: REGISTERED·ARRIVED 에서만 / 미루기: REGISTERED 에서만 (`WaitingStatus`)
- 예상 대기 = `manualWaitMin ?: (REGISTERED·ARRIVED 팀 수 × minutesPerTeam)` (`QueueCalculator`)
- 순번 미루기 = `max(1, ceil(delayMin / minutesPerTeam))` 팀 뒤로, 최대 `maxDefers`(기본 2) 회 (`DeferPolicy`)
- 번호표 = 매장별·영업일(Asia/Seoul)별 1부터. 발급 시 매장 행 잠금

## 8. 데이터 모델 (`V1__init_schema.sql`)

| 테이블 | 핵심 컬럼 |
| --- | --- |
| `store` | name, address, latitude, longitude, poi_id, poi_sub_id, minutes_per_team, arrival_buffer_min, manual_wait_min, max_defers, avg_stay_min, open/close_time, pos_pin_hash |
| `device` | device_token(임의), consent_version, consented_at, revoked_at |
| `journey` / `journey_stop` | party_size, party_source, status / store_id, seq(1..3), status, waiting_id |
| `waiting` | store_id, journey_id, device_id, channel(VEHICLE/WALK_IN), business_date, ticket_no, party_size, status, queue_order, defer_count, approval_method, eta_min, version(낙관적 락) |
| `arrival_check` | result, alighted, party_size, arrived/door_opened/door_locked/belt_released/settled_at, occupancy_before/after |
| `waiting_event` / `journey_event` | 감사 로그 · KPI |
| `idempotency_record` | device_id + idem_key 유니크, request_hash, 저장 응답 |
| `kpi_daily` (V2) | business_date PK, 하루치 KPI JSON — 원본 파기 전에 남김 |

## 9. Gleo 함수 ↔ 서버 API

| 함수 (schema.json) | 앱이 부르는 서버 API | 콜백 data 재료 |
| --- | --- | --- |
| `OPTIMIZE_DAILY_ROUTE` | `POST /routes/optimize` | orderedStoreIds → 매장 이름 |
| `REGISTER_GOLDEN_TIME_WAITING` | `POST /waitings` (approvalMethod=VOICE, Idempotency-Key = 승인 카드 ID) | ticketNo, aheadTeams |
| `CHECK_WAITING_STATUS` | `GET /journeys/current` | aheadTeams, estimatedEntryAt |

화면 승인과 음성 승인이 거의 동시에 와도 **같은 Idempotency-Key** 를 쓰면 웨이팅은 하나만 생긴다.
