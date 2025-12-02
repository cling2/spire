#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
COMPOSE="docker compose"

echo "[1] spire-server 기동..."
$COMPOSE up -d spire-server

echo "[2] 서버 준비될 때까지 3초 대기..."
sleep 3

echo "[3] trust bundle 받아서 agent bootstrap.crt로 저장 (secure 모드용)"
mkdir -p "${ROOT_DIR}/spire/agent"
# insecure_bootstrap=true면 이 파일은 그냥 무시되므로 실패해도 전체 스크립트가 죽지 않게 || true
$COMPOSE exec -T spire-server /opt/spire/bin/spire-server bundle show \
  > "${ROOT_DIR}/spire/agent/bootstrap.crt" || true

echo "[4] join token 생성 (spiffe://example.org/host/spire-agent-1)..."
TOKEN=$($COMPOSE exec -T spire-server \
  /opt/spire/bin/spire-server token generate \
    -spiffeID spiffe://example.org/host/spire-agent-1 \
    -ttl 60000 | awk '/Token:/ {print $2}')

echo "   생성된 TOKEN: ${TOKEN}"

echo "[5] .env 파일에 JOIN_TOKEN 저장..."
cat > "${ROOT_DIR}/.env" <<EOF
JOIN_TOKEN=${TOKEN}
EOF

echo "[6] spire-agent 기동..."
$COMPOSE up -d spire-agent

echo "[7] java workload용 registration entry 생성..."
# 지금 구조는 java-app이 컨테이너 안에서 root(UID=0)로 돌아간다고 가정
$COMPOSE exec -T spire-server /opt/spire/bin/spire-server entry create \
  -parentID spiffe://example.org/host/spire-agent-1 \
  -spiffeID spiffe://example.org/workload/java-app \
  -selector unix:uid:0

echo "[8] java-app도 같이 올리려면 (원하면 주석 풀기)"
# $COMPOSE up -d java-app

echo "---------------------------------------------"
echo "SPIRE 초기 설정 완료"
echo "현재 Compose 구조에서는 java-app 컨테이너 안에서"
echo "SPIFFE_ENDPOINT_SOCKET=unix:///tmp/spire-agent/public/api.sock 으로 이미 설정되어 있음."
echo "호스트에서 직접 Java를 띄우고 싶으면:"
echo "  export SPIFFE_ENDPOINT_SOCKET=unix:/tmp/spire-agent/public/api.sock"
echo "  ./gradlew bootRun (또는 java -jar ...)"
echo "---------------------------------------------"

