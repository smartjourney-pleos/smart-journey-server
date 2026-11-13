#!/usr/bin/env bash
# 데모 직전 점검 (Phase 5) — /actuator/health · POS 로그인 · STOMP 업그레이드 · 매장 대기 0팀
#
#   bash scripts/demo-check.sh                              # http://localhost:8080
#   bash scripts/demo-check.sh https://api.smartjourney.example
#   bash scripts/demo-check.sh <URL> --reset                # 매장 대기열 초기화까지 (리허설 시작 전)
#   STORES="1 2" PIN=1234 bash scripts/demo-check.sh <URL>

set -uo pipefail
BASE="${1:-http://localhost:8080}"
RESET="${2:-}"
STORES="${STORES:-1 2}"
PIN="${PIN:-1234}"
source "$(dirname "$0")/_lib.sh"

echo "Smart Journey 데모 점검 → $BASE"

step "서버"
req GET /actuator/health
[[ "$CODE" == 200 && "$RESP" == *'"UP"'* ]] && ok "health UP" || bad "health ($CODE) $RESP"
req GET /v3/api-docs
[[ "$CODE" == 200 ]] && ok "Swagger 문서" || bad "Swagger ($CODE)"
req GET /api/v1/consent/notice
[[ "$CODE" == 200 ]] && ok "고지 문구 버전 $(jget 'd["version"]')" || bad "고지 문구 ($CODE)"

step "WebSocket (/ws)"
ws_code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 --http1.1 \
    -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
    -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' "$BASE/ws")"
[[ "$ws_code" == 101 ]] && ok "업그레이드 101" || bad "업그레이드 응답 $ws_code (프록시 Upgrade 설정 확인)"

for sid in $STORES; do
    step "매장 $sid POS"
    pos_login "$sid" "$PIN"
    if [[ -z "$JWT" ]]; then bad "로그인 실패 ($CODE) $RESP"; continue; fi
    ok "PIN 로그인"
    AUTH="Authorization: Bearer $JWT"
    if [[ "$RESET" == "--reset" ]]; then
        req POST /api/v1/pos/demo/reset '' "$AUTH"
        [[ "$CODE" == 200 ]] && ok "대기열 초기화 ($(jget 'd["changedTeams"]')팀 정리)" || bad "초기화 ($CODE) $RESP"
    fi
    req GET /api/v1/pos/queue '' "$AUTH"
    ahead="$(jget 'd.get("aheadTeams")')"
    [[ "$ahead" == 0 ]] && ok "대기 0팀" || bad "대기 ${ahead}팀 남음 (--reset 으로 초기화)"
    req GET /api/v1/pos/settings '' "$AUTH"
    echo "    설정: $(jget '"팀당 %s분 · 도착 여유 %s분 · 수동 대기 %s" % (d["minutesPerTeam"], d["arrivalBufferMin"], d.get("manualWaitMin","없음"))')"
done

echo
if [[ $FAILED -eq 0 ]]; then printf '\033[1;32m모두 통과\033[0m\n'; else printf '\033[1;31m%d건 실패\033[0m\n' "$FAILED"; exit 1; fi
