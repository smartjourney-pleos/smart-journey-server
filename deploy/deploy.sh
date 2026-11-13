#!/usr/bin/env bash
# 개발 PC → VM 배포 (Docker 없음): 테스트 → bootJar → 업로드 → systemd 재시작 → health 확인
#
#   bash deploy/deploy.sh ubuntu@<VM>            # 테스트 포함
#   bash deploy/deploy.sh ubuntu@<VM> --skip-tests
#
# VM 은 deploy/setup-vm.sh 로 한 번 준비돼 있어야 한다. 저장소 루트에서 실행.

set -euo pipefail

HOST="${1:?ssh 대상을 넣어 주세요 (예: ubuntu@3.35.10.20)}"
SKIP_TESTS="${2:-}"
cd "$(dirname "$0")/.."

say() { printf '\033[1;34m== %s\033[0m\n' "$*"; }

if [[ "$SKIP_TESTS" != "--skip-tests" ]]; then
    say "테스트"
    ./gradlew test
fi

say "bootJar"
./gradlew bootJar -x test
JAR="$(ls build/libs/*.jar | grep -v -- '-plain' | head -1)"
echo "$JAR ($(du -h "$JAR" | cut -f1))"

say "업로드"
scp "$JAR" "$HOST:/tmp/smartjourney.jar"

say "교체·재시작"
ssh "$HOST" 'set -e
  sudo install -o smartjourney -g smartjourney -m 640 /tmp/smartjourney.jar /opt/smartjourney/app.jar
  rm -f /tmp/smartjourney.jar
  sudo systemctl restart smartjourney
  for i in $(seq 1 60); do
    if curl -fs http://127.0.0.1:8080/actuator/health | grep -q "\"UP\""; then
      echo "UP (${i}s)"; exit 0
    fi
    sleep 1
  done
  echo "60초 안에 안 떴습니다. 로그:"; sudo journalctl -u smartjourney -n 80 --no-pager; exit 1'

say "끝 — bash scripts/demo-check.sh https://<도메인>"
