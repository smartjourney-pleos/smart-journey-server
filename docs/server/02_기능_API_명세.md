# 서버 기능 · API 명세 v1

> 작성 2026-11-10 · 기준: 개발 명세서 v3.1 6장 + Pleos SDK 분석(01 문서) 반영본
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
| S1 | 고지·동의·기기 토큰, 철회 시 전송 차단 | P0-6 | ✅ Phase 0 구현 |
| S2 | 매장 목록·상세 (좌표·POI 포함), 대기 현황(골든타임 입력값) | P0-2 | ✅ Phase 0 구현 |
| S3 | 여정(코스) 생성·조회·복구·인원 변경·종료 | P0-1, P0-8 | Phase 1 |
| S4 | 원격 웨이팅 접수 (멱등, 번호표, 순번) | P0-3 | Phase 1 |
| S5 | 실시간 동기화 (STOMP 3토픽, 커밋 후 전송) | P0-4 | 뼈대 ✅ / 이벤트 Phase 1~2 |
| S6 | POS 로그인·대기열 보드·데모 패널 | P0-4 | 로그인 ✅ / 나머지 Phase 1 |
| S7 | ETA 전달, 도착·하차 확인 | P0-5 | Phase 2 |
| S8 | 호출(QR)·착석·노쇼 | P0-5 | Phase 2 (QR 코덱 ✅) |
| S9 | 매장 설정 (팀당 분·도착 여유·수동 대기) | P0-4 | ✅ Phase 0 구현 |
| S10 | 인원 변동 반영 | P1-1 | Phase 3 |
| S11 | 코스 순서 제안 (카카오) | P1-2 | none 모드 ✅ / 카카오 Phase 3 |
| S12 | 음성용 상태 요약 (Gleo 재료) | P1-3 | Phase 3 (`/journeys/current` 재사용) |
| S13 | KPI 이벤트 수집 | PRD 지표 | Phase 3 |
| S14 | 순번 미루기, 순서 변경 | P2-1 | Phase 4 (정책 ✅) |
| S15 | 보유 기간 파기, 처리방침 페이지 | 정책 4 | 페이지 ✅ / 파기 Phase 4 |

## 2. 인증

| 대상 | 방식 | 발급 |
| --- | --- | --- |
| 차량 앱 | 헤더 `X-Device-Token: <43자>` | `POST /api/v1/devices` (동의 버튼) |
| 매장 POS | 헤더 `Authorization: Bearer <JWT>` (12시간) | `POST /api/v1/pos/login` (매장 + PIN) |
| STOMP | CONNECT 네이티브 헤더에 위와 같은 값 | – |

토큰은 URL에 절대 넣지 않는다 (Pleos 앱 보안 가이드).

## 3. REST API — 차량 앱

| Method | Path | 요청 | 응답 / 비고 | Phase |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/consent/notice` | – | `{version, items[], notCollected[], recipients[], purpose, retentionDays, refusalRight, privacyPolicyPath}` 인증 불필요 | 0 ✅ |
| POST | `/api/v1/devices` | `{consentVersion}` | 201 `{deviceToken, consentVersion, consentedAt}` 인증 불필요. 버전이 최신 아니면 400 | 0 ✅ |
| GET | `/api/v1/devices/me` | – | `{consentActive, reconsentRequired, …}` 철회 상태에서도 가능 | 0 ✅ |
| PUT | `/api/v1/devices/me/consent` | `{consentVersion}` | 재동의 / 고지 버전 갱신 | 0 ✅ |
| DELETE | `/api/v1/devices/me/consent` | – | 철회 → 이후 403 `CONSENT_REQUIRED` | 0 ✅ |
| GET | `/api/v1/stores` | – | `[{id, name, address, latitude, longitude, poiId, poiSubId, openNow, avgStayMin, queue{…}}]` | 0 ✅ |
| GET | `/api/v1/stores/{id}` | – | 위 한 건 | 0 ✅ |
| GET | `/api/v1/stores/{id}/queue-status` | – | `{storeId, aheadTeams, estimatedWaitMin, arrivalBufferMin, manualOverride, calculatedAt}` | 0 ✅ |
| POST | `/api/v1/routes/optimize` | `{storeIds[], firstLegMin{storeId:분}}` | `{orderedStoreIds[], legsMin[], totalTravelMin, optimized, provider}` 현재 위치 좌표는 안 받음 | 0(none) / 3 |
| POST | `/api/v1/journeys` | `{partySize, partySource, storeIds[]}` | 201 여정. 기기당 ACTIVE 1개 | 1 |
| GET | `/api/v1/journeys/current` | – | 진행 중 여정 / 없으면 204. 앱 복구·Gleo 브리핑 재료 | 1 |
| GET | `/api/v1/journeys/{id}` | – | `{journeyId, status, partySize, stops[{seq, storeId, storeName, status, waiting{…}}]}` | 1 |
| PATCH | `/api/v1/journeys/{id}/party` | `{partySize, partySource}` | 정차 중 인원 수정 | 1 |
| PUT | `/api/v1/journeys/{id}/stops` | `{storeIds[]}` | 순서 변경 (승인 후) | 4 |
| POST | `/api/v1/journeys/{id}/end` | – | 남은 REGISTERED 웨이팅은 취소 | 1 |
| POST | `/api/v1/journeys/{id}/events` | `{type, storeId, attributes, occurredAt}` | 202. KPI 이벤트 | 3 |
| POST | `/api/v1/waitings` | 헤더 `Idempotency-Key` + `{journeyId, storeId, partySize, approvalMethod}` | 201 `{waitingId, ticketNo, aheadTeams, estimatedWaitMin, estimatedEntryAt, …}` | 1 |
| GET | `/api/v1/waitings/{id}` | – | 위와 같은 모양 | 1 |
| PATCH | `/api/v1/waitings/{id}/party-size` | `{partySize}` | 409 `WAITING_ALREADY_CALLED` | 3 |
| POST | `/api/v1/waitings/{id}/defer` | `{delayMin}` | 409 `DEFER_LIMIT_EXCEEDED` | 4 |
| PUT | `/api/v1/waitings/{id}/eta` | `{etaMin}` | 2분 이상 바뀔 때 또는 1분 주기 | 2 |
| POST | `/api/v1/waitings/{id}/arrival` | `{result, alighted, partySize, evidence{…}}` | 하차 확인 | 2 |
| GET | `/api/v1/waitings/{id}/ticket` | – | `{ticketNo, qrPayload, expiresAt}` CALLED 이후 | 2 |
| DELETE | `/api/v1/waitings/{id}` | – | 취소 | 1 |

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
| GET | `/api/v1/pos/stores` | 로그인 화면 매장 목록 (인증 불필요) | 0 ✅ |
| POST | `/api/v1/pos/login` | `{storeId, pin}` → `{accessToken, expiresAt, storeId, storeName}` | 0 ✅ |
| GET | `/api/v1/pos/queue` | `{aheadTeams, estimatedWaitMin, items[{ticketNo, partySize, channel, vehicleVerified, status, etaMin, arrival{result, alighted, partySize}, …}]}` | 1 |
| GET | `/api/v1/pos/waitings/{id}` | 팀 상세 + 이벤트 기록 | 2 |
| POST | `/api/v1/pos/waitings/{id}/call` | 차량에 CALLED + QR | 2 |
| POST | `/api/v1/pos/waitings/{id}/seat` | 착석 | 2 |
| POST | `/api/v1/pos/waitings/{id}/no-show` | 노쇼 | 2 |
| GET / PATCH | `/api/v1/pos/settings` | `{minutesPerTeam, arrivalBufferMin, manualWaitMin, clearManualWait}` → QUEUE_UPDATED | 0 ✅ |
| POST | `/api/v1/pos/demo/walk-ins` | `{count, partySize}` 현장 팀 +N | 1 |
| DELETE | `/api/v1/pos/demo/walk-ins?count=1` | 현장 팀 −N | 1 |
| POST | `/api/v1/pos/demo/reset` | 오늘 대기열 초기화 (리허설) | 4 |
| POST | `/api/v1/pos/tickets/verify` | `{qrPayload}` 입장 QR 확인 | 4 |

데모 API는 `smartjourney.demo.enabled=false` 면 403 `DEMO_DISABLED`.

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

## 6. 에러

모든 에러 `{code, message, fields?}`. 클라이언트는 `code` 로 분기.

| HTTP | code |
| --- | --- |
| 400 | `INVALID_REQUEST`, `PARTY_SIZE_OUT_OF_RANGE`, `TOO_MANY_STOPS`, `ARRIVAL_INVALID`, `IDEMPOTENCY_KEY_REQUIRED` |
| 401 | `UNAUTHORIZED`, `POS_LOGIN_FAILED` |
| 403 | `CONSENT_REQUIRED`, `FORBIDDEN`, `DEMO_DISABLED` |
| 404 | `STORE_NOT_FOUND`, `JOURNEY_NOT_FOUND`, `WAITING_NOT_FOUND`, `NOT_FOUND` |
| 409 | `WAITING_ALREADY_CALLED`, `INVALID_STATE_TRANSITION`, `DEFER_LIMIT_EXCEEDED`, `DUPLICATE_ACTIVE_WAITING`, `ACTIVE_JOURNEY_EXISTS`, `IDEMPOTENCY_CONFLICT`, `CONCURRENT_UPDATE` |
| 501 | `NOT_IMPLEMENTED` — 메시지에 담당 Phase 표시 |

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

## 9. Gleo 함수 ↔ 서버 API

| 함수 (schema.json) | 앱이 부르는 서버 API | 콜백 data 재료 |
| --- | --- | --- |
| `OPTIMIZE_DAILY_ROUTE` | `POST /routes/optimize` | orderedStoreIds → 매장 이름 |
| `REGISTER_GOLDEN_TIME_WAITING` | `POST /waitings` (approvalMethod=VOICE, Idempotency-Key = 승인 카드 ID) | ticketNo, aheadTeams |
| `CHECK_WAITING_STATUS` | `GET /journeys/current` | aheadTeams, estimatedEntryAt |

화면 승인과 음성 승인이 거의 동시에 와도 **같은 Idempotency-Key** 를 쓰면 웨이팅은 하나만 생긴다.
