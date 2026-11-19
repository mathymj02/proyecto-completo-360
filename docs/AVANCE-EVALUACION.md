# Avance frente a EP5, EP6 y Evaluación 3

Actualizado el 19 de noviembre de 2026. **Implementado en código** significa que existe una versión en `main` compilable; **demostrado** significa que se observó funcionar con una prueba. La evaluación puede exigir ambas cosas y evidencia visual.

**Restricción del laboratorio observada:** VocLabs rechazó con `explicit deny` el arranque de la instancia al cambiarla a `t3.xlarge`. Se devolvió a `t3.micro` y arrancaron los nueve contenedores anteriores; Nginx respondió `200 OK` desde la propia EC2. La IPv4 pública comunicada siguió siendo `100.48.154.224`. No iniciar los clústeres en los 913 MiB de RAM actuales.

**Actualización 19-11-2026:** frontend y catálogo cargan desde la EC2 original. La segunda instancia `i-05a83d666c15001df` ejecuta tres ZooKeeper, tres brokers Kafka, Kafka UI, dos nodos RabbitMQ en el mismo clúster y `analytics-recommendations`. ZooKeeper devolvió `[1, 2, 3]` para `/brokers/ids`; ambos topics principales tienen tres particiones, tres réplicas e ISR `1,2,3`. Kafka UI devolvió HTTP `200`. Una prueba sintética publicó `VIEW_PRODUCT` en Kafka y aumentó `productViews`. El usuario agregó un producto desde el frontend: el `carrito-api` nuevo publicó `ADD_TO_CART` y analítica mostró `cartAdds: 1`, verificando el primer flujo real entre ambas EC2. RabbitMQ mostró ambos nodos sin alarmas ni particiones de red. La EC2 original alcanzó por red privada los puertos `5672` y `29092` de la nueva. Aún falta migrar los consumidores RabbitMQ y comprobar offsets/lag del grupo Kafka.

| Requisito | Estado comprobado | Falta para demostrarlo |
| --- | --- | --- |
| Angular, Entra ID, JWT, API Gateway para API y RabbitMQ básico | Demostrado en evaluación anterior: compra encolada con catálogo detenido y stock actualizado al reiniciarlo. | Repetir tras el nuevo despliegue; probar token válido e inválido. |
| Ruta de API Gateway hacia el frontend y CORS | CORS de la IP HTTPS funcionó en un preflight anterior. | Crear/probar integración al Nginx, rutas para `/` y recursos Angular, verificar origen actual. |
| RabbitMQ: 2 nodos, 3 colas y DLQ, exchange directo y topic | Dos nodos en un clúster, sin alarmas ni particiones; conexión privada `5672` desde la EC2 original. Declaraciones y políticas DLQ en código; prueba unitaria de idempotencia de stock pasó. | Redirigir servicios, comprobar tres colas y DLQ, enviar un mensaje fallido a cada DLQ y probar enrutamiento topic. |
| Kafka: 3 ZooKeeper, 3 brokers, UI, 2 topics 3 particiones/3 réplicas | Tres ZooKeeper y tres brokers `Up`; Kafka UI responde `200`. Ambos topics descritos con 3 particiones, 3 réplicas e ISR `1,2,3`. Un evento sintético y un `ADD_TO_CART` real pasaron por el consumidor de analítica; el segundo elevó `cartAdds` a 1. | Probar `VIEW_PRODUCT` desde frontend nuevo y `intentos-pago`; mostrar consumer group, offsets y lag en Kafka UI. |
| Administración de topics/colas y errores | Scripts de creación, listado, descripción, retención y DLQ en el árbol local; DLT Kafka en el consumidor. | Probar scripts y caminos de error en contenedores reales; revisar si la rúbrica exige un microservicio administrador separado. |
| Pagos Webpay y antifraude | `ms-pago` y frontend de retorno implementados localmente; pruebas unitarias del servicio pasaron. El antifraude usa un alias de tarjeta de demostración, no datos reales de Webpay. | Configurar credenciales de integración, ruta AWS, activar frontend y completar pago de prueba de extremo a extremo. |
| DynamoDB | Código de escritura opcional con SDK Java y perfil IAM previsto. | Crear tabla y rol, activar bandera y verificar ítems escritos. |
| WebSocket con al menos 3 métricas | Servicio de analítica en AWS responde JSON de cuatro contadores; `productViews` aumentó tras consumir Kafka. Endpoint WebSocket y panel Angular en `main`. | Configurar proxy HTTPS del frontend y probar la conexión WebSocket en el navegador con al menos tres métricas cambiando. |
| README profesional, reproducibilidad y GitHub `main` | Código nuevo y documentación están en `main`; README describe arquitectura, identidad, mensajería y límites de lo comprobado. | Completar evidencias de integración y actualizar el estado final tras el despliegue. |

## Ruta crítica para la presentación

1. Mantener la EC2 original operativa y usar la segunda `t3.large` ya creada para los clústeres y servicios nuevos. El aumento previo a 20 GiB fue solo del disco y `t3.xlarge` fue rechazado al arrancar.
2. Verificar Kafka UI y mensajes reales; levantar el RabbitMQ nuevo y comprobar ambos nodos. Mantener intacto el volumen del RabbitMQ anterior hasta cambiar los consumidores de forma controlada y aplicar políticas DLQ.
3. Desplegar los nuevos servicios y revisar que el frontend, los seis servicios anteriores y la compra todavía funcionen.
4. Configurar rutas y CORS en API Gateway, credenciales de prueba Webpay, tabla y rol DynamoDB.
5. Registrar evidencia: Kafka UI con tres brokers/replicas/offsets, Rabbit UI con dos nodos y DLQ, pago Webpay, tabla DynamoDB y dashboard WebSocket actualizado.

El cálculo honesto del avance es: **base anterior comprobada; código nuevo en `main`; clúster Kafka y replicación de topics comprobados; integración y demostración extremo a extremo pendientes**. No asignar un porcentaje de nota a partir de archivos sin evidencia en AWS.
