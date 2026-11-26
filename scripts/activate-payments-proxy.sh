#!/usr/bin/env bash
set -euo pipefail

# Ejecutar en la EC2 original, siempre desde el checkout limpio de GitHub.
root=/home/ec2-user/proyecto-completo-360-core
cd "$root"
compose=(docker compose --project-directory "$root" --env-file "$root/.env" -p proyecto-completo
  -f "$root/docker-compose.live-ec2.yml"
  -f "$root/docker-compose.frontend-gateway-ec2.yml")

status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 \
  http://172.31.42.125:8087/api/v1/payments)
if [[ "$status" != 401 ]]; then
  echo "ms-pago respondió HTTP $status; se esperaba 401 sin JWT" >&2
  exit 1
fi

"${compose[@]}" config --quiet
docker run --rm --entrypoint nginx \
  -v "$root/frontend/nginx.gateway.conf:/etc/nginx/conf.d/default.conf:ro" \
  -v /home/ec2-user/proyecto-completo/frontend/ssl:/etc/nginx/ssl:ro \
  proyecto-completo-frontend:latest -t

"${compose[@]}" up -d --no-deps --force-recreate frontend
status=$(curl -ksS -o /dev/null -w '%{http_code}' --max-time 10 \
  https://127.0.0.1/api/v1/payments)
if [[ "$status" != 401 ]]; then
  echo "Ruta Nginx de pagos respondió HTTP $status; revisar frontend" >&2
  exit 1
fi
echo 'OK: ms-pago y Nginx responden 401 sin JWT; ruta privada activa'
