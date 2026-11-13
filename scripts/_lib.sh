# 공통 함수 (demo-check / demo-scenario / load-check 가 source 한다)
# 필요: curl, python3 (macOS 기본 포함)
#
# req 는 결과를 전역 변수로 남긴다 (서브셸 $(...) 로 부르면 CODE 가 사라지므로):
#   req POST /api/v1/xxx '{"a":1}' "헤더: 값" ...   →  $CODE (HTTP 코드), $RESP (본문)

BASE="${BASE:-http://localhost:8080}"
BASE="${BASE%/}"
FAILED=0
CODE=000
RESP=""

ok()   { printf '  \033[1;32m✔\033[0m %s\n' "$*"; }
bad()  { printf '  \033[1;31m✘\033[0m %s\n' "$*"; FAILED=$((FAILED + 1)); }
step() { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }

# jget '<python 식>' — $RESP 를 d 로 받아 식 결과 출력.  예: jget 'd["waitingId"]'
jget() { printf '%s' "$RESP" | python3 -c "import json,sys
try: d=json.load(sys.stdin)
except Exception: d={}
v=$1
print(json.dumps(v, ensure_ascii=False) if isinstance(v,(dict,list)) else v)"; }

req() {
    local method="$1" path="$2" body="${3:-}"
    shift $(( $# < 3 ? $# : 3 ))
    local tmp; tmp="$(mktemp)"
    local args=(-s -o "$tmp" -w '%{http_code}' --max-time 10 -X "$method" "$BASE$path" -H 'Content-Type: application/json')
    local h; for h in "$@"; do args+=(-H "$h"); done
    [[ -n "$body" ]] && args+=(--data "$body")
    CODE="$(curl "${args[@]}")" || CODE=000
    RESP="$(cat "$tmp")"; rm -f "$tmp"
}

# pos_login <storeId> <pin> → $JWT (실패 시 빈 값)
pos_login() {
    req POST /api/v1/pos/login "{\"storeId\":$1,\"pin\":\"$2\"}"
    JWT=""; [[ "$CODE" == 200 ]] && JWT="$(jget 'd["accessToken"]')"
}

# new_device → $DEV (최신 고지 버전으로 동의)
new_device() {
    req GET /api/v1/consent/notice
    local version; version="$(jget 'd["version"]')"
    req POST /api/v1/devices "{\"consentVersion\":\"$version\"}"
    DEV="$(jget 'd["deviceToken"]')"
}
