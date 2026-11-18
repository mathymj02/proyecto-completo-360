#!/usr/bin/env bash
set -euo pipefail

# En la EC2 nueva los clientes internos usan el listener INTERNAL:9092.
# En una sola EC2 se conserva el listener 29092 del overlay original.
if [[ "${KAFKA_DEPLOYMENT:-single}" == "platform" ]]; then
  compose=(docker compose -f docker-compose.platform-ec2.yml)
  bootstrap="kafka-1:9092,kafka-2:9092,kafka-3:9092"
else
  compose=(docker compose -f docker-compose.yml -f docker-compose.streaming.yml)
  bootstrap="kafka-1:29092,kafka-2:29092,kafka-3:29092"
fi
topic_cmd=("${compose[@]}" exec -T kafka-1 kafka-topics --bootstrap-server "$bootstrap")

case "${1:-}" in
  create)
    "${topic_cmd[@]}" --create --if-not-exists --topic navegacion-productos --partitions 3 --replication-factor 3 --config retention.ms=604800000 --config cleanup.policy=delete
    "${topic_cmd[@]}" --create --if-not-exists --topic intentos-pago --partitions 3 --replication-factor 3 --config retention.ms=2592000000 --config cleanup.policy=delete
    "${topic_cmd[@]}" --create --if-not-exists --topic navegacion-productos.DLT --partitions 3 --replication-factor 3 --config retention.ms=2592000000
    "${topic_cmd[@]}" --create --if-not-exists --topic intentos-pago.DLT --partitions 3 --replication-factor 3 --config retention.ms=2592000000
    ;;
  list)
    "${topic_cmd[@]}" --list
    ;;
  describe)
    "${topic_cmd[@]}" --describe --topic "${2:?Indica el nombre del topico}"
    ;;
  delete)
    "${topic_cmd[@]}" --delete --topic "${2:?Indica el nombre del topico}"
    ;;
  retention)
    "${compose[@]}" exec -T kafka-1 kafka-configs --bootstrap-server "$bootstrap" --entity-type topics --entity-name "${2:?Indica el nombre del topico}" --alter --add-config "retention.ms=${3:?Indica milisegundos}"
    ;;
  *)
    echo "Uso: $0 {create|list|describe TOPICO|delete TOPICO|retention TOPICO MILISEGUNDOS}" >&2
    exit 2
    ;;
esac
