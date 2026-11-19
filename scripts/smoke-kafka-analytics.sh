#!/usr/bin/env bash
set -euo pipefail

# Prueba de integración en la EC2 nueva: Kafka -> consumidor -> métricas HTTP.
# Crea un evento sintético en el topic de navegación; no modifica el catálogo SQL.
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
views() {
  curl -fsS "$metrics_url" | python3 -c 'import json,sys; print(json.load(sys.stdin)["productViews"])'
}

before=$(views)
event_id="smoke-$(date +%s)"
occurred_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
printf '{"eventId":"%s","userId":"smoke-test","type":"VIEW_PRODUCT","productId":999999,"productName":"Prueba de integracion Kafka","category":"prueba","quantity":1,"occurredAt":"%s"}\n' \
  "$event_id" "$occurred_at" |
  timeout 30 docker compose -f docker-compose.platform-ec2.yml exec -T kafka-1 \
    kafka-console-producer --bootstrap-server kafka-1:9092 --topic navegacion-productos

for attempt in $(seq 1 15); do
  current=$(views)
  if (( current > before )); then
    echo "OK: Kafka -> analytics-recommendations; productViews: $before -> $current"
    echo "Evento de prueba: $event_id"
    exit 0
  fi
  sleep 2
done

echo "El evento se publicó, pero productViews no subió desde $before" >&2
echo "Revisa: docker compose -f docker-compose.platform-ec2.yml logs --tail=80 analytics-recommendations" >&2
exit 1
