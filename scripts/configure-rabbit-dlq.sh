#!/usr/bin/env bash
set -euo pipefail

# Ejecutar desde la raiz, DESPUES de iniciar orders-service para que Spring
# declare pedidos_dlx, las colas principales y las tres DLQ.
if [[ "${RABBIT_DEPLOYMENT:-single}" == "platform" ]]; then
  compose=(docker compose -f docker-compose.platform-ec2.yml)
  broker=rabbitmq-1
else
  compose=(docker compose)
  broker=rabbitmq
fi

"${compose[@]}" exec -T "$broker" rabbitmqctl set_policy dlx-stock '^stock_queue$' '{"dead-letter-exchange":"pedidos_dlx","dead-letter-routing-key":"dead.stock"}' --apply-to queues
"${compose[@]}" exec -T "$broker" rabbitmqctl set_policy dlx-notifications '^notifications_queue$' '{"dead-letter-exchange":"pedidos_dlx","dead-letter-routing-key":"dead.notifications"}' --apply-to queues
"${compose[@]}" exec -T "$broker" rabbitmqctl set_policy dlx-envios '^envios_queue$' '{"dead-letter-exchange":"pedidos_dlx","dead-letter-routing-key":"dead.envios"}' --apply-to queues

"${compose[@]}" exec -T "$broker" rabbitmqctl list_policies
