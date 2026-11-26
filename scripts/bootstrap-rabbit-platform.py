#!/usr/bin/env python3
"""Declara la topología RabbitMQ de la EC2 nueva mediante Management API.

Es idempotente y no lee ni modifica el RabbitMQ anterior. Las credenciales
permanecen en .env local; nunca se muestran en la salida.
"""

import base64
import json
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import ProxyHandler, Request, build_opener


ROOT = Path(__file__).resolve().parent.parent
ENV = ROOT / ".env"
BASE = "http://127.0.0.1:15672/api"
VHOST = "%2F"
QUEUE_ROUTES = {
    "stock": ("stock_queue", "stock_dlq", "dead.stock"),
    "notifications": ("notifications_queue", "notifications_dlq", "dead.notifications"),
    "envios": ("envios_queue", "envios_dlq", "dead.envios"),
}


def read_env():
    if not ENV.is_file():
        raise SystemExit("Falta .env en la raíz del proyecto")
    values = {}
    for line in ENV.read_text(encoding="utf-8").splitlines():
        if line and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value.strip().strip('"\'')
    for key in ("RABBIT_USER", "RABBIT_PASSWORD"):
        if not values.get(key) or values[key].startswith("CAMBIAR_"):
            raise SystemExit(f"Falta configurar {key} en .env local")
    return values


def main():
    env = read_env()
    basic = base64.b64encode(
        f'{env["RABBIT_USER"]}:{env["RABBIT_PASSWORD"]}'.encode("utf-8")
    ).decode("ascii")
    opener = build_opener(ProxyHandler({}))

    def api(method, path, payload=None):
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        request = Request(
            BASE + path,
            data=body,
            method=method,
            headers={
                "Authorization": "Basic " + basic,
                "Content-Type": "application/json",
            },
        )
        try:
            with opener.open(request, timeout=8) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except HTTPError as exc:
            raise SystemExit(f"RabbitMQ API devolvió HTTP {exc.code} en {method} {path}") from None
        except URLError as exc:
            raise SystemExit(f"RabbitMQ Management no responde en 127.0.0.1:15672: {exc.reason}") from None

    # Primero exchanges y políticas: las colas nuevas nacen con su DLX.
    for name, exchange_type in (
        ("pedidos_exchange", "direct"),
        ("pedidos_dlx", "direct"),
        ("pedidos_topic_exchange", "topic"),
    ):
        api("PUT", f"/exchanges/{VHOST}/{quote(name)}", {
            "type": exchange_type, "durable": True, "auto_delete": False,
            "internal": False, "arguments": {},
        })

    for label, (queue, dead_queue, dead_key) in QUEUE_ROUTES.items():
        api("PUT", f"/policies/{VHOST}/dlx-{label}", {
            "pattern": f"^{queue}$",
            "definition": {
                "dead-letter-exchange": "pedidos_dlx",
                "dead-letter-routing-key": dead_key,
            },
            "priority": 0,
            "apply-to": "queues",
        })
        for name in (queue, dead_queue):
            api("PUT", f"/queues/{VHOST}/{quote(name)}", {
                "durable": True, "auto_delete": False, "arguments": {},
            })
        api("POST", f"/bindings/{VHOST}/e/pedidos_exchange/q/{quote(queue)}", {
            "routing_key": "orden.creada", "arguments": {},
        })
        api("POST", f"/bindings/{VHOST}/e/pedidos_dlx/q/{quote(dead_queue)}", {
            "routing_key": dead_key, "arguments": {},
        })

    api("PUT", f"/queues/{VHOST}/orders_audit_queue", {
        "durable": True, "auto_delete": False, "arguments": {},
    })
    api("POST", f"/bindings/{VHOST}/e/pedidos_topic_exchange/q/orders_audit_queue", {
        "routing_key": "orders.#", "arguments": {},
    })

    names = {item["name"] for item in api("GET", f"/queues/{VHOST}")}
    expected = {"orders_audit_queue"}
    for queue, dead_queue, _ in QUEUE_ROUTES.values():
        expected.update((queue, dead_queue))
    missing = expected - names
    if missing:
        raise SystemExit("Faltan colas: " + ", ".join(sorted(missing)))

    print("RabbitMQ de plataforma: exchanges direct/topic y políticas DLX configurados")
    print("Colas principales: stock_queue, notifications_queue, envios_queue")
    print("DLQ: stock_dlq, notifications_dlq, envios_dlq")
    print("Cola topic: orders_audit_queue (orders.#)")
    print("Esto verifica la topología; la migración de los microservicios se prueba aparte")


if __name__ == "__main__":
    main()
