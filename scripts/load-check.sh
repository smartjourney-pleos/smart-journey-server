#!/usr/bin/env bash
# 음성 경로 응답 속도 점검 (3-4) — 네트워크 포함 접수·현황 p95. 목표 1초 미만 (Gleo 콜백 7초 제한)
#
#   bash scripts/load-check.sh                       # localhost, 30회, 매장 1
#   bash scripts/load-check.sh https://api.… 50 2     # URL, 횟수, 매장
# 끝나면 그 매장 대기열을 초기화한다. 결과는 측정 기록으로 남겨 둔다.

set -uo pipefail
BASE="${1:-http://localhost:8080}"
N="${2:-30}"
STORE="${3:-1}"
source "$(dirname "$0")/_lib.sh"

pos_login "$STORE" "${PIN:-1234}"
[[ -n "$JWT" ]] || { echo "POS 로그인 실패 ($CODE)" >&2; exit 1; }
AUTH="Authorization: Bearer $JWT"
req POST /api/v1/pos/demo/reset '' "$AUTH"

reg=(); cur=()
for i in $(seq 1 "$N"); do
    new_device
    req POST /api/v1/journeys "{\"partySize\":2,\"partySource\":\"SENSOR\",\"storeIds\":[$STORE]}" "X-Device-Token: $DEV"
    JID="$(jget 'd["journeyId"]')"
    reg+=("$(curl -s -o /dev/null -w '%{time_total}' -X POST "$BASE/api/v1/waitings" -H 'Content-Type: application/json' \
        -H "X-Device-Token: $DEV" -H "Idempotency-Key: load-$i-$RANDOM$RANDOM" \
        --data "{\"journeyId\":$JID,\"storeId\":$STORE,\"partySize\":2,\"approvalMethod\":\"VOICE\"}")")
    cur+=("$(curl -s -o /dev/null -w '%{time_total}' "$BASE/api/v1/journeys/current" -H "X-Device-Token: $DEV")")
    printf '.'
done
echo
req POST /api/v1/pos/demo/reset '' "$AUTH"

stats() { printf '%s\n' "$@" | python3 -c "import sys,math
v=sorted(float(x) for x in sys.stdin if x.strip())
print('p50 %4.0fms · p95 %4.0fms · max %4.0fms' % (v[len(v)//2]*1000, v[math.ceil(len(v)*0.95)-1]*1000, v[-1]*1000))"; }
echo "접수  POST /waitings          $(stats "${reg[@]}")"
echo "현황  GET  /journeys/current  $(stats "${cur[@]}")"
echo "($(date '+%Y-%m-%d %H:%M') · $BASE · ${N}회 · 목표 p95 < 1000ms)"
