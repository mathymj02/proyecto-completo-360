#!/usr/bin/env bash
set -euo pipefail

# Solo evidencia sintética: no inicia Webpay ni cobra dinero.
# Kafka intentos-pago -> analytics-recommendations -> WebSocket/REST.
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  echo "Falta .env en la raíz del proyecto" >&2
  exit 1
fi
platform_ip=$(sed -n 's/^PLATFORM_PRIVATE_IP=//p' .env)
if [[ -z "$platform_ip" ]]; then
  echo "Falta PLATFORM_PRIVATE_IP en .env" >&2
  exit 1
fi
metrics_url="http://${platform_ip}:8088/api/v1/analytics/metrics"
snapshot() {
  curl -fsS "$metrics_url" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["paymentAttempts"], d["fraudAlerts"])'
}

read -r attempts_before alerts_before < <(snapshot)
event_id="smoke-payment-$(date +%s)"
occurred_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
printf '{"eventId":"%s","userId":"smoke-test","orderId":"SMOKE-ONLY","status":"BLOCKED","reason":"PRUEBA_SINTETICA","occurredAt":"%s"}\n' \
  "$event_id" "$occurred_at" |
  timeout 30 docker compose -f docker-compose.platform-ec2.yml exec -T kafka-1 \
    kafka-console-producer --bootstrap-server kafka-1:9092 --topic intentos-pago

for attempt in $(seq 1 15); do
  read -r attempts_after alerts_after < <(snapshot)
  if (( attempts_after > attempts_before && alerts_after > alerts_before )); then
    echo "OK: intentos-pago -> analytics-recommendations"
    echo "paymentAttempts: $attempts_before -> $attempts_after"
    echo "fraudAlerts: $alerts_before -> $alerts_after"
    echo "Evento sintético: $event_id; no es un pago Webpay"
    exit 0
  fi
  sleep 2
done

echo "El evento se publicó, pero las métricas no aumentaron" >&2
echo "Revisa logs de analytics-recommendations" >&2
exit 1
