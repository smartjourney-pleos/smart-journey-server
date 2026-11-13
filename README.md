# Smart Journey Server

스마트 저니 중계 서버. 차량 앱(Pleos Connect)과 매장 웹 POS 사이에서 웨이팅 접수·대기열·도착·하차 확인을 실시간으로 이어 준다.

```
차량 앱 ──REST(명령)──▶ 서버 ◀──REST(명령)── 매장 웹 POS
   ▲                     │                       ▲
   └──── STOMP(상태) ────┴────── STOMP(상태) ─────┘
```

## 기술 스택

Java 21 · Spring Boot 3.5 · Spring Web / WebSocket(STOMP) / Security / Data JPA · Flyway · PostgreSQL 17 (로컬·테스트는 H2 PostgreSQL 모드) · springdoc-openapi · Gradle 8.14

Docker 는 쓰지 않는다.

## 실행

JDK 21 이 없으면 Gradle 이 자동으로 받는다 (toolchain).

```bash
./gradlew bootRun                    # local 프로필 (H2 파일 DB, 데모 매장 자동 생성)
./gradlew test                       # 단위 + 통합 + STOMP 테스트
./gradlew test jacocoTestReport      # + 커버리지 → build/reports/jacoco/test/html/index.html
bash scripts/demo-scenario.sh        # 서버만으로 T1 흐름 (앱 없이)
```

| 주소 | 내용 |
| --- | --- |
| http://localhost:8080/swagger-ui.html | API 문서 (계약서) |
| http://localhost:8080/actuator/health | 상태 확인 |
| ws://localhost:8080/ws | STOMP |
| http://localhost:8080/privacy | 개인정보 처리방침 (초안) |
| http://localhost:8080/ops/kpi.html | 운영 지표 대시보드 (로컬 토큰 `local-ops`) |

- Pleos 에뮬레이터에서는 `http://10.0.2.2:8080`
- 데모 매장: `A 맛집`(id 1), `B 카페`(id 2) · POS PIN `1234`
- 로컬 DB 초기화: `data/` 폴더 삭제

### PostgreSQL 로 실행

```bash
cp .env.example .env       # DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, QR_SECRET 채우기
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```

postgres 프로필은 JWT_SECRET·QR_SECRET 이 개발용 기본값이면 부팅을 거부한다. 데모 매장은 `deploy/add-store.sql` 로 넣는다.

### 배포 (Docker 없음)

VM 한 대 + systemd + Caddy. 순서는 [docs/server/04_배포_데모_운영.md](docs/server/04_배포_데모_운영.md).

```bash
ssh ubuntu@<VM> 'sudo bash /tmp/setup-vm.sh <도메인>'   # 처음 한 번
bash deploy/deploy.sh ubuntu@<VM>                       # 매번
bash scripts/demo-check.sh https://<도메인> --reset      # 데모 직전
```

## 구조

```
src/main/java/com/smartjourney/server
├─ common/      config · error · security · realtime(STOMP) · entity · util
├─ device/      외부 전송 고지·동의·기기 토큰
├─ store/       매장 · 대기 현황(골든타임 입력값)
├─ journey/     여정(코스) · KPI 이벤트 · 동의 철회 처리
├─ waiting/     웨이팅 접수 · 순번 · 미루기 · ETA · 멱등키 · STOMP 발행 · 외부 플랫폼 어댑터(platform/)
├─ arrival/     도착·하차 확인
├─ pos/         매장 POS 로그인 · 대기열 · 설정 · 데모 패널
├─ route/       코스 순서 제안 (카카오모빌리티, 실패 시 입력 순서)
├─ ticket/      번호표 QR 서명
├─ privacy/     처리방침 페이지 · 보유 기간 파기
└─ kpi/         운영 지표 일 집계 · 스냅샷

deploy/         VM 준비 · 배포 · systemd · Caddy/Nginx · 매장 입력 SQL
scripts/        데모 점검 · 시나리오 · 응답 속도 측정
```

기능별 패키지 안은 `api`(컨트롤러·DTO) → `application`(서비스) → `domain`(엔티티·리포지토리·순수 규칙).

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/server/01_Pleos_SDK_분석.md](docs/server/01_Pleos_SDK_분석.md) | SDK 가 서버에 주는 값·단위, 심사 기준, 기존 문서와 어긋난 점 |
| [docs/server/02_기능_API_명세.md](docs/server/02_기능_API_명세.md) | 서버 기능 · REST · STOMP · 에러 · 상태 머신 · 데이터 모델 |
| [docs/server/03_Phase별_작업계획.md](docs/server/03_Phase별_작업계획.md) | Phase 0 ~ 6 작업과 완료 기준 · 진행 상태 |
| [docs/server/04_배포_데모_운영.md](docs/server/04_배포_데모_운영.md) | 배포 순서 · 2차 PT 체크리스트 · 사고 대응 · 전시 운영 |

## 지켜야 할 것

- 위치 좌표·이동 경로·VIN 은 받지도 저장하지도 않는다
- 토큰은 헤더로만 (URL 금지). 로그에서도 가려진다
- `.env`, `data/`, `docs/SmartJourney/01_제출용/` 은 커밋하지 않는다
