# Métricas en tiempo real con Kafka y WebSocket

El panel de Angular muestra vistas de productos, adiciones al carrito,
intentos de pago y alertas antifraude. Esas cifras se calculan en
`analytics-recommendations` al consumir eventos de los topics
`navegacion-productos` e `intentos-pago`. El servicio envía cada nuevo
resumen a las sesiones WebSocket conectadas en `/ws/metrics`.

```text
catálogo / carrito / pagos -> Kafka -> analytics-recommendations
                                       | GET /api/v1/analytics/metrics
                                       | WebSocket /ws/metrics
                                       v
                                  panel Angular
```

## Dos URL y dos protocolos

El frontend puede abrirse desde `https://100.48.154.224` o desde
`https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com`. Las peticiones
REST de métricas pasan por la ruta `$default` del gateway a Nginx y de allí
por la IP privada `172.31.42.125:8088`. La HTTP API no es un proxy WebSocket;
el navegador abre `wss://100.48.154.224/ws/metrics` directamente con el
Nginx HTTPS de la EC2 original. Nginx conserva `Upgrade` y `Connection` y
reenvía la conexión al servicio de analítica por la red privada.

El servicio permite únicamente los orígenes del frontend de EC2 y del
gateway en el handshake. El endpoint de métricas no transporta tokens de
sesión ni datos de tarjeta. Si cambia la IP pública, actualizar
`analyticsWsUrl` en `environment.prod.ts`, las URI del gateway y los orígenes
permitidos. El certificado HTTPS actual de la EC2 es autofirmado: antes de
probar WebSocket desde el gateway, el navegador debe haber aceptado ese
certificado en la URL directa de la EC2.

## Despliegue por etapas

1. Permitir TCP `8088` en el grupo de seguridad de la EC2 nueva **solo**
   desde el grupo de la EC2 original. El puerto queda ligado a la IP privada.
2. En la EC2 nueva, actualizar `main` y reconstruir únicamente
   `analytics-recommendations` con `docker-compose.platform-ec2.yml`.
3. En la EC2 original, actualizar `main`, comprobar Nginx con `nginx -t`,
   reconstruir solo la imagen `frontend` y recrear solo ese contenedor
   usando `docker-compose.live-ec2.yml` más
   `docker-compose.frontend-gateway-ec2.yml`. El segundo archivo monta la
   configuración Nginx y el certificado local sin subir su clave a GitHub.
4. Confirmar que el catálogo y la compra anteriores todavía funcionan.

La configuración de la EC2 original está en un archivo Compose **local**:
contiene diferencias de ese laboratorio y no se publica en el repositorio.
No usar `docker compose down -v` ni alterar los volúmenes de MariaDB o del
RabbitMQ original para desplegar el panel.

## Evidencia para la evaluación

- Abrir el panel y comprobar que recibe una instantánea inicial sin refrescar.
- Abrir el detalle de un producto y agregarlo al carrito. Eso debe aumentar
  `productViews` y `cartAdds` mediante eventos reales de Kafka.
- En la EC2 nueva, ejecutar `bash scripts/smoke-kafka-payments.sh` para
  publicar un evento **sintético** `BLOCKED` en `intentos-pago`: debe aumentar
  `paymentAttempts` y `fraudAlerts` en vivo. No es una transacción Webpay.
- En Kafka UI, mostrar los dos topics, sus tres particiones y réplicas, y
  el grupo `analytics-recommendations` con offsets y lag. La interfaz de
  Kafka UI está ligada a `127.0.0.1:8090` de la EC2 nueva.

Las métricas viven en memoria y se reinician al recrear analítica; los
eventos de Kafka permanecen según la retención configurada. DynamoDB es
una persistencia adicional opcional y se activa por separado.
