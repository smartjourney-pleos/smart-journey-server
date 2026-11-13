#!/usr/bin/env bash
# 차량 앱 없이 서버만으로 T1 흐름을 한 번 돌린다 (리허설·백업 시연 · Phase 5)
# 실행하는 동안 POS 화면을 띄워 두면 카드가 실시간으로 바뀌는 걸 볼 수 있다.
#
#   bash scripts/demo-scenario.sh                         # localhost, 42dot(1)
#   bash scripts/demo-scenario.sh <URL> [storeId] [PIN]
#   PAUSE=3 bash scripts/demo-scenario.sh                 # 단계마다 3초 쉼 (PT 화면 따라가기)

set -uo pipefail
BASE="${1:-http://localhost:8080}"
STORE="${2:-1}"
PIN="${3:-1234}"
PAUSE="${PAUSE:-1}"
source "$(dirname "$0")/_lib.sh"

need() { [[ "$CODE" == 2* ]] || { printf '  \033[1;31m✘\033[0m %s: HTTP %s %s\n' "$1" "$CODE" "$RESP" >&2; exit 1; }; }
pause() { sleep "$PAUSE"; }

step "준비: POS 로그인 + 대기열 초기화"
pos_login "$STORE" "$PIN"; need "POS 로그인"
AUTH="Authorization: Bearer $JWT"
req POST /api/v1/pos/demo/reset '' "$AUTH"; need "초기화"
ok "매장 $STORE 대기 0팀"

step "① 동의 → 기기 토큰"
new_device; need "동의"
H="X-Device-Token: $DEV"; ok "토큰 발급 (${DEV:0:6}…)"; pause

step "② 여정 시작: 4명(센서 제안), 매장 $STORE"
req POST /api/v1/journeys "{\"partySize\":4,\"partySource\":\"SENSOR\",\"storeIds\":[$STORE]}" "$H"; need "여정"
JID="$(jget 'd["journeyId"]')"; ok "journeyId=$JID"; pause

step "③ 현장 팀 +2 (이미 줄 서 있는 손님)"
req POST /api/v1/pos/demo/walk-ins '{"count":2,"partySize":2}' "$AUTH"; need "+2"
req GET /api/v1/stores/$STORE/queue-status '' "$H"
ok "대기 $(jget 'd["aheadTeams"]')팀 · 예상 $(jget 'd["estimatedWaitMin"]')분 · 도착 여유 $(jget 'd["arrivalBufferMin"]')분"; pause

step "④ 골든타임 승인 → 원격 웨이팅 접수 (POS 에 🚗 카드)"
req POST /api/v1/waitings "{\"journeyId\":$JID,\"storeId\":$STORE,\"partySize\":4,\"approvalMethod\":\"TOUCH\"}" "$H" "Idempotency-Key: card-$(date +%s)-$RANDOM"; need "접수"
WID="$(jget 'd["waitingId"]')"
ok "번호표 $(jget 'd["ticketNo"]')번 · 앞에 $(jget 'd["aheadTeams"]')팀 · 예상 $(jget 'd["estimatedWaitMin"]')분"; pause

step "⑤ T2 대기 급증: 현장 팀 +3 (심사위원 조작 장면)"
req POST /api/v1/pos/demo/walk-ins '{"count":3,"partySize":2}' "$AUTH"; need "+3"
req GET /api/v1/stores/$STORE/queue-status '' "$H"
ok "지금 새로 줄 서면 대기 $(jget 'd["estimatedWaitMin"]')분 (이미 접수한 내 순번은 그대로)"; pause

step "⑥ ETA 전달: 7분 → 2분"
req PUT /api/v1/waitings/$WID/eta '{"etaMin":7}' "$H"; need "ETA"; ok "도착 7분 전"; pause
req PUT /api/v1/waitings/$WID/eta '{"etaMin":2}' "$H"; need "ETA"; ok "도착 2분 전"; pause

step "⑦ 도착·하차 확인: 4명 중 3명 하차 (CONFIRMED → POS 초록 배지)"
req POST /api/v1/waitings/$WID/arrival '{"result":"CONFIRMED","alighted":3,"partySize":4,"evidence":{"occupancyBefore":4,"occupancyAfter":1}}' "$H"; need "도착"
ok "상태 $(jget 'd["waitingStatus"]') · $(jget 'd["alighted"]')/$(jget 'd["partySize"]')명 하차"; pause

step "⑧ 앞 팀 착석 → 호출 → 차량에 번호표 QR"
req GET /api/v1/pos/queue '' "$AUTH"
AHEAD_IDS="$(jget '" ".join(str(i["waitingId"]) for i in d["items"][: [x["waitingId"] for x in d["items"]].index('"$WID"')])')"
for id in $AHEAD_IDS; do
    req POST /api/v1/pos/waitings/$id/call '' "$AUTH"; need "앞 팀 호출"
    req POST /api/v1/pos/waitings/$id/seat '' "$AUTH"; need "앞 팀 착석"
done
ok "앞 팀 $(echo $AHEAD_IDS | wc -w | tr -d ' ')팀 착석"
req POST /api/v1/pos/waitings/$WID/call '' "$AUTH"; need "호출"
req GET /api/v1/waitings/$WID/ticket '' "$H"; need "번호표"
QR="$(jget 'd["qrPayload"]')"
ok "호출됨 · QR ${QR:0:12}… (만료 $(jget 'd["expiresAt"]'))"; pause

step "⑨ 입장 QR 확인 → 착석"
req POST /api/v1/pos/tickets/verify "{\"qrPayload\":\"$QR\"}" "$AUTH"; need "QR 확인"
[[ "$(jget 'd["valid"]')" == True ]] && ok "QR 유효" || bad "QR 무효"
req POST /api/v1/pos/waitings/$WID/seat '' "$AUTH"; need "착석"
req GET /api/v1/journeys/$JID '' "$H"
ok "착석 → 여정 $(jget 'd["status"]')"

echo; [[ $FAILED -eq 0 ]] && printf '\033[1;32m시나리오 끝\033[0m (뒤에 남은 현장 팀은 demo-check.sh --reset 으로 정리)\n' || exit 1
