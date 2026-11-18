#!/usr/bin/env python3
"""Genera secretos locales de RabbitMQ cuando .env aún tiene ejemplos."""

from pathlib import Path
import secrets


env_path = Path(__file__).resolve().parent.parent / ".env"
if not env_path.is_file():
    raise SystemExit("Falta .env en la raíz del proyecto. Copia .env.example primero.")

values = {
    "RABBIT_PASSWORD": "CAMBIAR_EN_ENTORNO_LOCAL",
    "RABBIT_ERLANG_COOKIE": "CAMBIAR_POR_VALOR_ALEATORIO_LARGO",
}
lines = env_path.read_text(encoding="utf-8").splitlines(keepends=True)
found = set()
updated = set()
result = []

for line in lines:
    key, separator, value = line.partition("=")
    if separator and key in values:
        if key in found:
            raise SystemExit(f"Clave duplicada en .env: {key}")
        found.add(key)
        if value.strip() == values[key]:
            line = f"{key}={secrets.token_hex(24)}\n"
            updated.add(key)
    result.append(line)

missing = values.keys() - found
if missing:
    raise SystemExit(f"Faltan claves en .env: {', '.join(sorted(missing))}")

if updated:
    env_path.write_text("".join(result), encoding="utf-8")

print("RabbitMQ: contraseña y cookie configuradas en .env local.")
print("Se generaron claves nuevas para:", ", ".join(sorted(updated)) or "ninguna")
print("El archivo .env no debe subirse a GitHub ni enviarse por chat.")
