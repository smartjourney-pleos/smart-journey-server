# 서버 Phase별 작업 계획

> 작성 2026-11-10 · 갱신 2026-11-13 (Phase 1~6 서버 코드 반영) · 팀 일정(개발 명세서 10장)과 공모전 일정에 맞춤
> 표기: ✅ 끝 · ⬜ 할 일 · 🤝 앱/POS 담당과 맞출 것 · 🙋 사람이 직접 (계정·키·클라우드·리허설)

| Phase | 기간 | 목표 | 서버 코드 | 남은 일 |
| --- | --- | --- | --- | --- |
| 0 | 11/10 | 프로젝트 뼈대 | ✅ | – |
| 1 | 11/11 – 11/14 | 핵심 루프: 여정 → 접수 → POS 보드 → 데모 패널 | ✅ | 🤝 1-8 앱 실제 연동 |
| 2 | 11/16 – 11/22 | 도착·하차 확인, 호출·QR, 실시간 이벤트 | ✅ | 🤝 T5 (앱 쪽) |
| 3 | 11/23 – 11/27 | 인원 변동, 코스 최적화, 음성, KPI | ✅ | 🙋 카카오 키 발급 · 🤝 3-3 |
| 4 | 11/28 – 12/02 | 클라우드 배포(Docker 없이), 예외·보안, P2 | ✅ (배포 파일 준비) | 🙋 VM 생성·도메인 · 🤝 4-10 |
| 5 | 12/03 – 12/04 | **2차 PT 라이브 데모** | ✅ 점검·시나리오 스크립트 | 🙋 리허설 3회 |
| 6 | 12월 – 2027.02 | 전시용 안정화 | ✅ 대시보드·커버리지·어댑터 | 필요 시 브로커 교체 |

테스트: **66개 전부 통과** (`./gradlew test`), 줄 커버리지 91% (`./gradlew test jacocoTestReport`).
PostgreSQL(16)에서 `postgres` 프로필 부팅·`ddl-auto: validate`·데모 시나리오·서버 재시작(T8)까지 확인.

---

## Phase 0 — 뼈대 (11/10) ✅

- ✅ Pleos SDK 문서·팀 문서 분석 → `01_Pleos_SDK_분석.md` · 서버 기능·API → `02_기능_API_명세.md`
- ✅ Spring Boot 3.5 / Java 21 / Gradle 8.14, Docker 미사용 · 프로필 `local` / `postgres` / `test`
- ✅ Flyway V1 스키마 + 데모 매장 시드 · 공통 에러·Clock·Swagger·CORS · 기기 토큰 / POS JWT / STOMP 인가
- ✅ 로컬 `./gradlew test` 통과 확인 (11/10)

---

## Phase 1 — 핵심 루프 ✅

| # | 작업 | 상태 | 확인 방법 |
| --- | --- | --- | --- |
| 1-1 | `JourneyService` create / current / get / changeParty / end | ✅ | 기기당 ACTIVE 1개(409), 4곳 400 `TOO_MANY_STOPS`, current 204/200 — `JourneyWaitingFlowTest` |
| 1-2 | `WaitingService.create` 멱등키·매장 잠금·번호표·JourneyStop 연결 | ✅ | 같은 키 → 같은 웨이팅, 다른 바디 409, **동시 8건 번호 중복 없음 · 같은 키 동시 4건 → 1개** |
| 1-3 | `WaitingService.get / cancel` | ✅ | 취소 → POS 보드에서 빠짐, 마지막 매장이면 여정 종료 |
| 1-4 | `WAITING_CREATED`(POS) · `QUEUE_UPDATED`(매장) | ✅ | 실제 포트 STOMP 테스트 — **접수 → POS 2초 안** (`StompRealtimeTest`) |
| 1-5 | `PosQueueService.queue` | ✅ | queue_order 순, 🚗 `vehicleVerified` |
| 1-6 | 데모 walk-in +N / −N (QUEUE_UPDATED 1회) | ✅ | +3 → 대기 15분 증가, 이벤트 1회 확인 |
| 1-7 | 통합 테스트 여정 → 접수 → POS 보드 | ✅ | `./gradlew test` |
| 1-8 | 로컬 시연: 에뮬레이터 → `http://10.0.2.2:8080` | 🤝 ⬜ | 앱이 실제 서버로 접수 성공 (CORS·WS Origin 에 10.0.2.2 허용돼 있음) |

🤝 **앱 담당**: DTO 는 Swagger 기준 · Idempotency-Key = 승인 카드 ID (화면·음성 같은 키) · STOMP 재연결 후 `GET /journeys/current` → `GET /waitings/{id}` 로 재동기화
🤝 **POS 담당**: `/topic/pos/{storeId}` payload 는 전부 `/pos/queue` 의 카드 한 장 → waitingId 로 교체/삭제 · `QUEUE_UPDATED` 로 상단 숫자 갱신

---

## Phase 2 — 도착·호출 ✅

| # | 작업 | 상태 | 확인 |
| --- | --- | --- | --- |
| 2-1 | `updateEta` + `ETA_UPDATED` | ✅ | POS 카드 `etaMin` |
| 2-2 | `ArrivalService.report` — 검증·ARRIVED 전이·근거 저장 | ✅ | CONFIRMED/MANUAL/UNCONFIRMED 배지, 잘못된 값 400 `ARRIVAL_INVALID` |
| 2-3 | CALLED 뒤 도착 보고 → 상태 유지, 기록만 | ✅ | `calledBeforeArrival` |
| 2-4 | 호출 + `CALLED {qrPayload}` + `GET /ticket` | ✅ | QR TTL 30분, 서명 검증, 다른 매장 POS 에선 무효 |
| 2-5 | seat / noShow → 정차 DONE, `WAITING_CLOSED` | ✅ | 보드에서 빠짐, 착석 후 QR 재사용 불가 |
| 2-6 | `WAITING_UPDATED` (순번 변화 시 줄 선 차량 모두에게) | ✅ | 앞 팀 호출 → "앞에 1팀" → 0팀 |
| 2-7 | POS 팀 상세 + 이벤트 기록 | ✅ | REGISTERED → ETA → ARRIVAL → CALLED → SEATED 순서 |
| 2-8 | STOMP 인가: 남의 웨이팅·남의 매장 토픽 거부 | ✅ | ERROR 프레임 `FORBIDDEN`, 토큰 없는 CONNECT 거부 |

E2E 서버 쪽: **T1 정상 흐름 ✅ · T2 대기 급증 ✅ · T6 하차 실패→수동 ✅** · T5 하차 흔들림은 앱 `AlightDetector` 담당

---

## Phase 3 — 차별점 보강 ✅

| # | 작업 | 상태 | 비고 |
| --- | --- | --- | --- |
| 3-1 | `changePartySize` + `PARTY_SIZE_CHANGED` | ✅ | 호출 후 409 `WAITING_ALREADY_CALLED` (T10 서버 쪽) |
| 3-2 | 카카오모빌리티 매장↔매장 소요시간 → 순서 최적화 | ✅ 코드 / 🙋 키 | `.env` 에 `ROUTE_PROVIDER=kakao`, `KAKAO_REST_API_KEY=…`. 타임아웃 2초, 실패 시 입력 순서 |
| 3-3 | 🤝 앱: `requestTrip` 으로 firstLegMin 구하기 | ⬜ | 좌표는 서버로 안 감 |
| 3-4 | 접수·현황 p95 < 1s | ✅ | 로컬 측정 **접수 p95 105ms · 현황 p95 30ms** (`scripts/load-check.sh`, `ResponseTimeTest`) |
| 3-5 | KPI 이벤트 + 일 집계 | ✅ | `GET /api/v1/ops/kpi/daily` (X-Ops-Token) · 좌표·음성 원문 키 거절 |
| 3-6 | 동의 철회 시 진행 중 웨이팅 정책 | ✅ | **여정 종료 + 줄 선 웨이팅 취소 (POS 에 알림), 호출된 팀은 그대로** |
| 3-7 | `ddl-auto: validate` | ✅ | H2 · PostgreSQL 모두 부팅 확인 |

---

## Phase 4 — 배포·안정화 ✅ (코드·배포 파일) / 🙋 클라우드 작업

**Docker 없이 배포** — 순서는 `04_배포_데모_운영.md`

| # | 작업 | 상태 | 비고 |
| --- | --- | --- | --- |
| 4-1 | VM: JDK 21, PostgreSQL 17, DB·계정 | ✅ 스크립트 / 🙋 실행 | `deploy/setup-vm.sh <도메인>` — .env 비밀값 자동 생성 |
| 4-2 | bootJar → 업로드 → **systemd** | ✅ 스크립트 / 🙋 실행 | `deploy/deploy.sh ubuntu@<VM>` (테스트 → 업로드 → 재시작 → health) |
| 4-3 | HTTPS/WSS 리버스 프록시 | ✅ 설정 / 🙋 도메인 | `deploy/Caddyfile` (자동 인증서) 또는 `deploy/nginx-smartjourney.conf` |
| 4-4 | 운영 매장 입력 | ✅ | `deploy/add-store.sql` (PIN 은 pgcrypto bcrypt — 서버 로그인 확인함) |
| 4-5 | 순번 미루기 · 순서 변경 | ✅ | 뒤에 팀 없으면 횟수 안 씀, 2회 초과 409 |
| 4-6 | `demo/reset`, `tickets/verify` | ✅ | |
| 4-7 | PIN 잠금 · 로그 마스킹 · CORS 운영 도메인 | ✅ | 5분/5회 → 429, 토큰·PIN·JWT·QR 로그에서 가림, `ALLOWED_ORIGINS` env. postgres 프로필은 개발용 비밀값이면 **부팅 거부** |
| 4-8 | `DataRetentionJob` (30일) | ✅ | 매일 04:30, 파기 전 KPI 스냅샷(`kpi_daily`) |
| 4-9 | **T8** 서버 30초 중지 → 재시작 후 상태 일치 | ✅ 서버 쪽 | 재시작 후 여정·웨이팅 그대로, 같은 키 재시도 → 같은 번호표. 🤝 앱·POS 재연결 배너 확인 |
| 4-10 | 🤝 release APK 운영 주소, POS 배포 URL | ⬜ | 도메인 정해지면 |

---

## Phase 5 — 2차 PT 라이브 데모

- ✅ `scripts/demo-check.sh <URL> --reset` — health · Swagger · WS 101 · 매장별 로그인·대기 0팀
- ✅ `scripts/demo-scenario.sh <URL>` — 앱 없이 T1 흐름 9단계 (백업 시연·리허설용, `PAUSE=3` 로 천천히)
- ✅ 체크리스트 → `04_배포_데모_운영.md`
- 🙋 ⬜ 리허설 3회 (기록은 04 문서 표에)
- 🙋 ⬜ 백업: 노트북 로컬 서버(`./gradlew bootRun`) + 핫스팟, Fake flavor APK

---

## Phase 6 — 전시용 안정화

- ✅ KPI 대시보드 `/ops/kpi.html` (운영 토큰)
- ✅ 테스트 커버리지 JaCoCo — 줄 91% · 분기 73%
- ✅ 부하 점검 스크립트 `scripts/load-check.sh`
- ✅ 상용 웨이팅 플랫폼 어댑터 계층 — `ExternalWaitingPlatform` 구현 빈 하나 추가하면 커밋 후 이벤트를 받는다 (실패해도 우리 흐름은 계속)
- ⬜ 서버 2대 이상이 필요해지면 Simple Broker → 외부 브로커(STOMP relay) 검토. 지금은 1대 + 인메모리 브로커로 충분

---

## 매일 지킬 것

- 커밋 전 `./gradlew test`
- API 바꾸면 Swagger 확인 후 앱·POS 담당에게 공유
- `.env`, `data/`, `docs/SmartJourney/01_제출용/` 커밋 금지 (.gitignore 에 있음)
- 확인되지 않은 신호는 성공 시나리오로 시연하지 않는다 (정책 8)
