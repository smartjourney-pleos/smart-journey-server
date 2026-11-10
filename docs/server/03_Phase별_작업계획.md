# 서버 Phase별 작업 계획

> 작성 2026-11-10 · 팀 일정(개발 명세서 10장)과 공모전 일정에 맞춤
> 표기: ✅ 끝 · ⬜ 할 일 · 🤝 앱/POS 담당과 맞출 것

| Phase | 기간 | 목표 | 마감 산출물 |
| --- | --- | --- | --- |
| 0 | 11/10 | 프로젝트 뼈대 | 이 저장소 초기 커밋 |
| 1 | 11/11 – 11/14 | 핵심 루프: 여정 → 접수 → POS 보드 → 데모 패널 | **11/15 1차 접수** 시연 영상용 서버 |
| 2 | 11/16 – 11/22 | 도착·하차 확인, 호출·QR, 실시간 이벤트 완성 | E2E T1·T2·T5·T6 통과 |
| 3 | 11/23 – 11/27 | 차별점 보강: 인원 변동, 코스 최적화, 음성, KPI | E2E 1차 (T1~T10) |
| 4 | 11/28 – 12/02 | 클라우드 배포(Docker 없이), 예외·보안, P2 | WSS 배포 URL, release APK 연동 |
| 5 | 12/03 – 12/04 | **2차 PT 라이브 데모** 운영 | 리허설 3회 |
| 6 | 12월 – 2027.02 | 전시용 안정화 | 최종 APK + 운영 서버 |

---

## Phase 0 — 뼈대 (11/10) ✅

- ✅ Pleos SDK 문서·팀 문서 전체 분석 → `01_Pleos_SDK_분석.md`
- ✅ 서버 기능·API 확정 → `02_기능_API_명세.md`
- ✅ Spring Boot 3.5 / Java 21 / Gradle 8.14 (wrapper 포함), Docker 미사용
- ✅ 프로필: `local`(H2 파일, 기본) · `postgres` · `test`(H2 메모리)
- ✅ Flyway V1 스키마 8테이블 + 데모 매장 시드(A 맛집, B 카페, PIN 1234)
- ✅ 공통: 에러 형식 `{code, message}`, Clock 빈, Swagger, CORS
- ✅ 보안: 기기 토큰 필터(철회 시 403), POS JWT, STOMP CONNECT/SUBSCRIBE 인가
- ✅ 실시간: 토픽·봉투·커밋 후 전송(`RealtimeRelay`)
- ✅ 구현 끝난 API: 고지·동의·철회, 매장 목록·대기 현황, POS 로그인·설정, 코스 제안(none)
- ✅ 순수 도메인 + 단위 테스트: `QueueCalculator`, `DeferPolicy`, `WaitingStatus`, `ArrivalPolicy`, `QrTicketCodec`, `PermutationRouteOptimizer`
- ✅ 통합 테스트: 동의 → 토큰 → 철회 → 403, POS 로그인
- ⬜ **로컬에서 `./gradlew test` 한 번 돌려 확인** (작성 환경에서 Maven Central 접근이 막혀 빌드는 못 돌림)

---

## Phase 1 — 핵심 루프 (11/11 – 11/14)

**목표**: 골든타임 승인 한 번이면 2초 안에 POS 보드에 팀이 뜨고, POS 데모 패널로 대기를 바꾸면 차량 숫자가 바로 바뀐다.

| # | 작업 | 관련 | 완료 기준 |
| --- | --- | --- | --- |
| 1-1 | `JourneyService` create / current / get / changeParty / end | P0-1, P0-8 | 기기당 ACTIVE 1개, 3곳 초과 400, 앱 재시작 시 current 로 복구 |
| 1-2 | `WaitingService.create` — 멱등키, 매장 잠금, 번호표, JourneyStop 연결 | P0-3 | 같은 키 재요청 → 같은 응답 / 다른 바디 → 409. 동시 2건도 번호 중복 없음 |
| 1-3 | `WaitingService.get / cancel` | – | 취소 시 QUEUE_UPDATED |
| 1-4 | 이벤트 발행: `WAITING_CREATED`(POS), `QUEUE_UPDATED`(매장) | P0-4 | STOMP 테스트 클라이언트로 수신 확인 |
| 1-5 | `PosQueueService.queue` | P0-4 | queue_order 순, 차량 채널 표시 |
| 1-6 | `PosDemoService` walk-in +N / −N (QUEUE_UPDATED 1회만) | P0-4, T2 | 현장 팀 +3 → 차량 대기시간 2초 안에 변함 |
| 1-7 | 통합 테스트: 여정 → 접수 → POS 보드 | – | `./gradlew test` 통과 |
| 1-8 | 로컬 시연 환경: 에뮬레이터 → `http://10.0.2.2:8080` | 🤝 앱 | 앱이 실제 서버로 접수 성공 |

🤝 **앱 담당과 맞출 것**: DTO 필드명(Swagger 기준), Idempotency-Key 생성 규칙(승인 카드 ID), STOMP 재연결 후 REST 재동기화 순서
🤝 **POS 담당과 맞출 것**: `/api/v1/pos/queue` 응답 모양, 로그인 토큰 보관

---

## Phase 2 — 도착·호출 (11/16 – 11/22)

**목표**: "3/4명 하차" 가 POS 에 초록 배지로 뜨고, 점주가 호출하면 차량에 번호표가 뜬다.

| # | 작업 | 관련 | 완료 기준 |
| --- | --- | --- | --- |
| 2-1 | `updateEta` + `ETA_UPDATED` | P0-5 보조 | POS 카드 "도착 N분 전" 갱신 |
| 2-2 | `ArrivalService.report` — ArrivalPolicy 검증, ARRIVED 전이, 근거 저장 | **P0-5** | CONFIRMED/MANUAL/UNCONFIRMED 별 배지, 잘못된 값 400 |
| 2-3 | 이미 CALLED 상태에서 도착 보고 → 상태 유지, 기록만 | – | 도착 전 호출 케이스 |
| 2-4 | `PosQueueService.call` + `CALLED {qrPayload}` + `GET /waitings/{id}/ticket` | V-11 | QR TTL 30분, 서명 검증 |
| 2-5 | `seat` / `noShow` → JourneyStop DONE, `WAITING_CLOSED` | – | 목록에서 빠짐 |
| 2-6 | `WAITING_UPDATED` (순번·예상 입장 변화 시 차량에 푸시) | N-01 | 앞 팀이 착석하면 "앞에 N팀" 숫자 감소 |
| 2-7 | POS 팀 상세 + 이벤트 기록 | P-03 | – |
| 2-8 | STOMP 인가 테스트: 남의 웨이팅 토픽 구독 거부 | 보안 | – |

E2E: **T1 정상 흐름, T2 대기 급증, T5 하차 흔들림(앱 쪽), T6 하차 실패→수동**

---

## Phase 3 — 차별점 보강 (11/23 – 11/27)

| # | 작업 | 관련 | 완료 기준 |
| --- | --- | --- | --- |
| 3-1 | `changePartySize` + `PARTY_SIZE_CHANGED` | **P1-1**, T10 | 호출 후 409 |
| 3-2 | 카카오모빌리티 키 발급 → 매장↔매장 소요시간 → `PermutationRouteOptimizer` | P1-2 | 실패 시 입력 순서 폴백, 키는 `.env` 만 |
| 3-3 | 🤝 앱: `requestTrip` 으로 firstLegMin 구하기 확인 | P1-2 | 좌표가 서버로 안 감 |
| 3-4 | Gleo 경로 응답 속도 점검 (접수·현황 p95 < 1s) | P1-3 | 로컬 측정 기록 |
| 3-5 | `recordEvent` KPI 이벤트 + 일 집계 쿼리 | PRD 지표 | 인원 제안 수락률, 승인 방식 비율 |
| 3-6 | 동의 철회 시 진행 중 웨이팅 처리 정책 확정·구현 | 정책 4 | – |
| 3-7 | `ddl-auto: validate` 로 올리기 (엔티티 ↔ 스키마 일치 확인) | – | 부팅 성공 |

E2E 1차: T1 ~ T10 전체

---

## Phase 4 — 배포·안정화 (11/28 – 12/02)

**Docker 없이 배포** (팀 협의)

| # | 작업 | 완료 기준 |
| --- | --- | --- |
| 4-1 | 클라우드 VM 1대: JDK 21, PostgreSQL 17 직접 설치, DB·계정 생성 | `postgres` 프로필로 부팅 |
| 4-2 | `./gradlew bootJar` → jar 업로드 → **systemd** 서비스 (재부팅 자동 시작) | `systemctl status` 정상 |
| 4-3 | HTTPS/WSS: Caddy 또는 Nginx 리버스 프록시 (+ 인증서) | `wss://…/ws` 연결 |
| 4-4 | 운영 매장 데이터 입력 스크립트 (데모 시드는 postgres 프로필에서 안 들어감) | – |
| 4-5 | 순번 미루기 `defer` / 순서 변경 `reorderStops` | P2-1 |
| 4-6 | `demo/reset`, `tickets/verify` | 리허설 |
| 4-7 | POS PIN 연속 실패 잠금, 로그 마스킹(토큰·PIN), CORS 운영 도메인 | 보안 가이드 |
| 4-8 | `DataRetentionJob` 파기 구현 (보유 30일) | 정책 4 |
| 4-9 | **T8 네트워크 단절**: 서버 30초 중지 → 재시작 후 차량·POS 상태 일치 | – |
| 4-10 | 🤝 release APK 에 운영 서버 주소, POS 배포 URL | – |

---

## Phase 5 — 2차 PT 라이브 데모 (12/03 – 12/04)

- ⬜ 리허설 3회: `demo/reset` → 시나리오 9장 순서대로 → 기록
- ⬜ 백업: 노트북 로컬 서버(`local` 프로필) + 핫스팟, Fake flavor APK
- ⬜ 데모 직전 체크: `/actuator/health`, POS 로그인, STOMP 연결 표시, 매장 대기 0팀
- ⬜ 심사위원 조작: POS 데모 패널 +3 → 차량 N-01 숫자 변화 (T2)

---

## Phase 6 — 전시용 안정화 (12월 – 2027.02)

- 지표 집계·대시보드, 테스트 커버리지, 부하 점검
- 서버 2대 이상이 필요해지면 Simple Broker → 외부 브로커(STOMP relay) 검토
- 상용 웨이팅 플랫폼 제휴 시 연동 어댑터 계층

---

## 매일 지킬 것

- 커밋 전 `./gradlew test`
- API 바꾸면 Swagger 확인 후 앱·POS 담당에게 공유
- `.env`, `data/`, `docs/SmartJourney/01_제출용/` 커밋 금지 (.gitignore 에 있음)
- 확인되지 않은 신호는 성공 시나리오로 시연하지 않는다 (정책 8)
