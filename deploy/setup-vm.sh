#!/usr/bin/env bash
# Smart Journey 서버 VM 준비 (Docker 없이) — Ubuntu 22.04 / 24.04, 한 번만 실행
#
#   scp deploy/setup-vm.sh deploy/smartjourney.service deploy/Caddyfile deploy/add-store.sql ubuntu@<VM>:/tmp/
#   ssh ubuntu@<VM> 'sudo bash /tmp/setup-vm.sh api.smartjourney.example'
#
# 하는 일: JDK 21 · PostgreSQL 17(PGDG) · Caddy 설치 → DB/계정 생성 → /opt/smartjourney/.env 생성(비밀값 자동)
#          → systemd 서비스 등록 → Caddy 설정 → 방화벽(22, 80, 443)
# 클라우드 콘솔의 보안 그룹에서도 80·443 을 열어야 한다. 8080 은 열지 않는다.

set -euo pipefail

DOMAIN="${1:?도메인을 넣어 주세요 (예: api.smartjourney.example 또는 <공인IP>.sslip.io)}"
APP_DIR=/opt/smartjourney
DB_NAME=smartjourney
DB_USER=smartjourney
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

[[ $EUID -eq 0 ]] || { echo "sudo 로 실행해 주세요" >&2; exit 1; }
say() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }

say "패키지"
apt-get update -y
apt-get install -y curl ca-certificates gnupg openjdk-21-jre-headless postgresql-common ufw \
    debian-keyring debian-archive-keyring apt-transport-https

say "PostgreSQL 17 (PGDG)"
if ! dpkg -s postgresql-17 >/dev/null 2>&1; then
    /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
    apt-get install -y postgresql-17 postgresql-contrib-17 || apt-get install -y postgresql-17
fi
systemctl enable --now postgresql

say "Caddy"
if ! command -v caddy >/dev/null; then
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
        | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
        > /etc/apt/sources.list.d/caddy-stable.list
    apt-get update -y && apt-get install -y caddy
fi

say "앱 계정·폴더"
id -u smartjourney >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin smartjourney
install -d -o smartjourney -g smartjourney -m 750 "$APP_DIR"

say "DB·계정"
if [[ ! -f "$APP_DIR/.env" ]]; then
    DB_PASSWORD="$(openssl rand -hex 24)"
    sudo -u postgres psql -v ON_ERROR_STOP=1 <<SQL
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DB_USER') THEN
    CREATE ROLE $DB_USER LOGIN PASSWORD '$DB_PASSWORD';
  ELSE
    ALTER ROLE $DB_USER PASSWORD '$DB_PASSWORD';
  END IF;
END \$\$;
SQL
    sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 \
        || sudo -u postgres createdb -O "$DB_USER" -E UTF8 "$DB_NAME"
    # 매장 PIN 해시(add-store.sql)용
    sudo -u postgres psql -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS pgcrypto"

    say ".env 생성 (비밀값 자동)"
    umask 077
    cat > "$APP_DIR/.env" <<ENV
DB_URL=jdbc:postgresql://localhost:5432/$DB_NAME
DB_USERNAME=$DB_USER
DB_PASSWORD=$DB_PASSWORD
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
QR_SECRET=$(openssl rand -base64 48 | tr -d '\n')
OPS_TOKEN=$(openssl rand -hex 16)
ROUTE_PROVIDER=none
KAKAO_REST_API_KEY=
ALLOWED_ORIGINS=https://$DOMAIN,http://localhost:*
DEMO_ENABLED=true
ENV
    chown smartjourney:smartjourney "$APP_DIR/.env"
    chmod 600 "$APP_DIR/.env"
else
    echo ".env 가 이미 있어 그대로 둔다: $APP_DIR/.env"
fi

say "systemd"
install -m 644 "$SRC_DIR/smartjourney.service" /etc/systemd/system/smartjourney.service
systemctl daemon-reload
systemctl enable smartjourney
echo "jar 를 올린 뒤 시작된다 (deploy/deploy.sh)"

say "Caddy (HTTPS/WSS)"
sed "s/{\$DOMAIN}/$DOMAIN/" "$SRC_DIR/Caddyfile" > /etc/caddy/Caddyfile
install -d -o caddy -g caddy /var/log/caddy
systemctl enable caddy
systemctl reload caddy || systemctl restart caddy

say "방화벽"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

say "끝"
cat <<MSG
다음:
  1) 개발 PC 에서  bash deploy/deploy.sh ubuntu@<VM>
  2) 매장 넣기     sudo -u postgres psql -d $DB_NAME -v name='A 맛집' -v address='…' -v lat=37.34 -v lng=126.73 -v pin=1234 -f /tmp/add-store.sql
  3) 확인          bash scripts/demo-check.sh https://$DOMAIN
  운영 지표 토큰:  sudo grep OPS_TOKEN $APP_DIR/.env
MSG
