# Guion breve de defensa técnica

Esta guía separa evidencia observada de pruebas pendientes. Revisar el estado
actual antes de presentarla: los laboratorios VocLabs pueden detener instancias
o cambiar direcciones IP públicas.

## Recorrido de cinco minutos

1. Abrir el frontend desde la [URL de API Gateway](https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com/), iniciar sesión y mostrar catálogo y carrito. Explicar que Angular usa MSAL para obtener un access token de Microsoft Entra ID; API Gateway enruta las rutas HTTP y un Lambda Authorizer protege rutas de negocio. El backend también valida JWT donde está configurado. CORS permite al navegador usar un origen distinto, pero no concede permisos.
2. Mostrar una compra normal. `orders-service` genera `ORD-...` y publica el evento en RabbitMQ. La respuesta HTTP confirma la publicación al broker; los consumidores trabajan después. Para demostrar la cola: detener catálogo, crear una orden, mostrar `stock_queue Ready=1`, reiniciar catálogo y mostrar `Ready=0` y el log de stock. Ya se observó esta secuencia en una prueba anterior.
3. Mostrar Kafka UI: tres brokers y los topics `navegacion-productos` e `intentos-pago`, cada uno con tres particiones y tres réplicas. Explicar que una partición distribuye registros; una réplica conserva una copia. La prueba real `ADD_TO_CART` fue consumida por analítica. Un evento de pago `BLOCKED` sintético incrementó `paymentAttempts` y `fraudAlerts`.
4. Mostrar `GET /api/v1/analytics/metrics` y el panel WebSocket. REST devuelve una instantánea cuando se solicita; WebSocket mantiene una conexión para enviar cambios sin refrescar. Se comprobó un handshake `101` y una instantánea JSON. La actualización visual en el panel debe verificarse antes de afirmarla.
5. Mostrar la tabla DynamoDB `Pedidos360Events` con el evento sintético. La EC2 nueva usa el perfil IAM `LabInstanceProfile`: el SDK obtiene credenciales temporales, sin claves AWS en `.env`. La consulta de recuento devolvió `Count: 1`. Los contadores de analítica viven en memoria y se reinician con el proceso; los eventos escritos permanecen en DynamoDB.

## Evidencias adicionales

| Tema | Evidencia disponible | Límite que debes mencionar |
| --- | --- | --- |
| RabbitMQ nuevo | Dos nodos en clúster, tres colas principales, tres DLQ, exchange directo y topic; los tres rechazos sintéticos llegaron a sus DLQ | Los consumidores de la compra aún usan el broker anterior hasta migrarlos |
| Webpay Plus | `ms-pago` y MariaDB propia iniciaron; código de integración y antifraude por alias en `main` | No presentar un cobro como exitoso sin completar aprobación, retorno y orden en el ambiente de prueba |
| API Gateway | Frontend, login, catálogo, CORS y analítica respondieron por Gateway | El origen HTTP público de EC2 y el certificado autofirmado son decisiones de laboratorio |
| GitHub | Código, Compose y documentación en `main` | `.env`, contraseñas y clave TLS no se publican |

## Respuestas cortas para preguntas frecuentes

- **¿Dockerfile, Compose o EC2 despliega el sistema?** EC2 aporta la máquina; cada Dockerfile construye la imagen de un servicio; Compose define y arranca el conjunto de contenedores, redes, puertos y volúmenes.
- **¿Qué es un broker?** Es el servidor que recibe y entrega mensajes. Aquí RabbitMQ y cada servidor Kafka son brokers, con modelos de consumo diferentes.
- **¿Qué es un topic?** Es la secuencia lógica de eventos en Kafka. Sus particiones guardan registros ordenados por partición y pueden tener réplicas.
- **¿Qué es una cola?** En RabbitMQ, cada consumidor recibe mensajes de su propia cola. Un mensaje en `Ready` espera consumidor; `Unacked` se entregó pero aún no se confirmó.
- **¿Qué es una DLQ?** Cola de mensajes que no se pudieron procesar. Permite inspeccionar fallas sin perder la solicitud original.
- **¿Qué es una UI?** Interfaz visual para inspeccionar el sistema; Kafka UI y la consola web de RabbitMQ muestran brokers, topics, colas y mensajes. No son los brokers mismos.
- **¿Por qué WebSocket?** Para enviar métricas al navegador sobre una conexión abierta sin pedir una página nueva a cada cambio.
- **¿Por qué dos EC2?** La original tiene 913 MiB de RAM y ya aloja los microservicios de compra. El laboratorio denegó ampliar su tipo. La segunda, con más memoria, aloja los clústeres y servicios nuevos; se comunican por direcciones privadas.

Consulta también [arquitectura y límites](../README.md),
[pagos](PAGOS-WEBPAY.md), [métricas](METRICAS-WEBSOCKET.md) y
[estado por requisito](AVANCE-EVALUACION.md).
