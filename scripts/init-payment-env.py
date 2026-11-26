#!/usr/bin/env python3
"""Prepara .env local de pagos de integración sin mostrar contraseñas.

Ejecutar solo en la EC2 de plataforma. Conserva cualquier secreto que ya
esté configurado y nunca escribe valores reales en el repositorio.
"""

import secrets
from pathlib import Path


ENV = Path(__file__).resolve().parent.parent / ".env"
GATEWAY = "https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com"
TEST_COMMERCE_CODE = "597055555532"
TEST_API_KEY = "579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C"


def main():
    if not ENV.is_file():
        raise SystemExit("Falta .env local en la raíz del proyecto")
    rows = ENV.read_text(encoding="utf-8").splitlines()
    values = {}
    for row in rows:
        if row and not row.lstrip().startswith("#") and "=" in row:
            key, value = row.split("=", 1)
            values[key] = value.strip()

    desired = {
        "PAYMENTS_DB_NAME": "pedidos360_pagos",
        "PAYMENTS_DB_USERNAME": "pagos",
        "PAYMENTS_DB_PASSWORD": secrets.token_hex(24),
        "PAYMENTS_DB_ROOT_PASSWORD": secrets.token_hex(24),
        "CARD_HASH_SALT": secrets.token_hex(32),
        "FRONTEND_BASE_URL": GATEWAY,
        "WEBPAY_COMMERCE_CODE": TEST_COMMERCE_CODE,
        "WEBPAY_API_KEY": TEST_API_KEY,
    }
    replace_if = {
        "FRONTEND_BASE_URL": lambda value: value in ("", "http://localhost:4200"),
        "WEBPAY_COMMERCE_CODE": lambda value: value in ("", "COMERCIO_DE_INTEGRACION"),
        "WEBPAY_API_KEY": lambda value: value in ("", "CLAVE_DE_INTEGRACION"),
    }
    changed = []
    for key, value in desired.items():
        current = values.get(key)
        should_set = current is None or current == "" or current.startswith("CAMBIAR_")
        if key in replace_if and current is not None:
            should_set = replace_if[key](current)
        if not should_set:
            continue
        found = False
        for index, row in enumerate(rows):
            if row.startswith(key + "="):
                rows[index] = key + "=" + value
                found = True
                break
        if not found:
            rows.append(key + "=" + value)
        changed.append(key)

    ENV.write_text("\n".join(rows) + "\n", encoding="utf-8")
    ENV.chmod(0o600)
    print("Pagos: .env local preparado para Webpay de integración y MariaDB propia")
    print("Variables configuradas: " + ", ".join(changed or ["ninguna; ya estaban definidas"]))
    print("Las contraseñas, claves y .env no se deben compartir ni subir a GitHub")


if __name__ == "__main__":
    main()
