# Avance frente a EP5, EP6 y Evaluación 3

Actualizado el 18 de noviembre de 2026. **Implementado en código** significa que existe una versión local compilable; **demostrado** significa que se observó funcionar con una prueba. La evaluación puede exigir ambas cosas y evidencia visual.

**Restricción del laboratorio observada:** VocLabs rechazó con `explicit deny` el arranque de la instancia al cambiarla a `t3.xlarge`. Se devolvió a `t3.micro` y arrancaron los nueve contenedores anteriores; Nginx respondió `200 OK` desde la propia EC2. La IPv4 pública comunicada siguió siendo `100.48.154.224`. No iniciar los clústeres en los 913 MiB de RAM actuales.

**Actualización posterior:** el usuario confirmó que frontend y catálogo cargan desde el navegador. `RunInstances --dry-run` rechazó `t3.xlarge` y aceptó `t3.large` (8 GiB). Se prepara un despliegue de dos EC2; aún no se creó ni verificó la segunda instancia.

| Requisito | Estado comprobado | Falta para demostrarlo |
| --- | --- | --- |
| Angular, Entra ID, JWT, API Gateway para API y RabbitMQ básico | Demostrado en evaluación anterior: compra encolada con catálogo detenido y stock actualizado al reiniciarlo. | Repetir tras el nuevo despliegue; probar token válido e inválido. |
| Ruta de API Gateway hacia el frontend y CORS | CORS de la IP HTTPS funcionó en un preflight anterior. | Crear/probar integración al Nginx, rutas para `/` y recursos Angular, verificar origen actual. |
| RabbitMQ: 2 nodos, 3 colas y DLQ, exchange directo y topic | Compose para dos nodos nuevos, declaraciones y políticas DLQ, publicación `orders.created` al TopicExchange; prueba unitaria de idempotencia de stock pasó. | Levantar clúster nuevo, comprobar ambos nodos, redirigir servicios, enviar mensaje fallido a cada DLQ y probar enrutamiento topic. |
| Kafka: 3 ZooKeeper, 3 brokers, UI, 2 topics 3 particiones/3 réplicas | Overlay y script de creación de topics escritos. Productores de catálogo, carrito y pagos; consumidor de analítica compilados. | Ampliar RAM, levantar clúster, comprobar replicación, mensajes, consumer group, offsets y lag en Kafka UI. |
| Administración de topics/colas y errores | Scripts de creación, listado, descripción, retención y DLQ en el árbol local; DLT Kafka en el consumidor. | Probar scripts y caminos de error en contenedores reales; revisar si la rúbrica exige un microservicio administrador separado. |
| Pagos Webpay y antifraude | `ms-pago` y frontend de retorno implementados localmente; pruebas unitarias del servicio pasaron. El antifraude usa un alias de tarjeta de demostración, no datos reales de Webpay. | Configurar credenciales de integración, ruta AWS, activar frontend y completar pago de prueba de extremo a extremo. |
| DynamoDB | Código de escritura opcional con SDK Java y perfil IAM previsto. | Crear tabla y rol, activar bandera y verificar ítems escritos. |
| WebSocket con al menos 3 métricas | Endpoint y panel Angular implementados localmente. | Desplegar, probar conexión WebSocket segura desde el frontend público y ver métricas cambiar con eventos. |
| README profesional, reproducibilidad y GitHub `main` | README de arquitectura anterior en `main`. Código nuevo y esta guía aún locales. | Actualizar README final, ejecutar pruebas de integración y subir una versión coherente a `main`. |

## Ruta crítica para la presentación

1. Confirmar con VocLabs/profesor qué capacidad de cómputo permite la política `Pvoclabs2`, o preparar un entorno alternativo con RAM suficiente. El aumento previo a 20 GiB fue solo del disco y `t3.xlarge` fue rechazado al arrancar.
2. Respaldar y migrar RabbitMQ existente antes de cambiar su volumen; levantar Kafka y Rabbit, crear topics y políticas DLQ.
3. Desplegar los nuevos servicios y revisar que el frontend, los seis servicios anteriores y la compra todavía funcionen.
4. Configurar rutas y CORS en API Gateway, credenciales de prueba Webpay, tabla y rol DynamoDB.
5. Registrar evidencia: Kafka UI con tres brokers/replicas/offsets, Rabbit UI con dos nodos y DLQ, pago Webpay, tabla DynamoDB y dashboard WebSocket actualizado.

El cálculo honesto del avance es: **base anterior comprobada; gran parte del código nuevo preparada; infraestructura y demostración integrada pendientes**. No asignar un porcentaje de nota a partir de archivos sin evidencia en AWS.
