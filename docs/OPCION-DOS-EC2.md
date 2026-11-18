# Alternativa: conservar la EC2 actual y crear otra

La EC2 original sigue sirviendo Angular, las API existentes, MariaDB y el RabbitMQ anterior desde `100.48.154.224` (`172.31.41.113` dentro de la VPC). La segunda EC2 `i-05a83d666c15001df` ya está ejecutándose como `t3.large`, con IP privada `172.31.42.125`. Docker 25.0.18 y Compose v2.30.3 están instalados; aún falta levantar y probar los servicios. La prueba `aws ec2 run-instances --dry-run` permitió `t3.large`, mientras que `t3.xlarge` fue denegada por `Pvoclabs2`. `StartInstances --dry-run` para la nueva EC2 devolvió `DryRunOperation`. Ambas instancias están en la misma subred/VPC y se comunicarán por IP privada.

```text
Navegador -> EC2 actual (frontend/API existentes/Rabbit anterior/MariaDB)
                     | IP privada, reglas de seguridad entre instancias
                     v
             EC2 nueva (Kafka 3 + ZooKeeper 3 + Rabbit 1 y 2 nuevos
                        + pagos + analítica + WebSocket)
```

## Lo que evita

No habría que sustituir de inmediato la IP pública del frontend actual ni las integraciones ya existentes del gateway que apuntan a esa EC2. CORS y la redirección de Entra para la URL actual pueden permanecer mientras la instancia antigua no cambie de IP.

## Trabajo adicional real

1. Los nombres `kafka-1`, `rabbitmq` y `mariadb` del Compose actual solo funcionan entre contenedores del **mismo host**. Los servicios distribuidos necesitan endpoints alcanzables mediante las IP privadas de las dos EC2. Los tres brokers Kafka deben anunciar direcciones y puertos accesibles desde la EC2 antigua; también hay que publicar esos puertos en Docker.
2. Crear reglas de grupos de seguridad **entre los grupos de ambas instancias**, restringidas a los puertos necesarios: Kafka, MariaDB, RabbitMQ y servicios HTTP. No abrir Kafka, MariaDB ni puertos de clúster Rabbit al público.
3. Los dos nodos Rabbit nuevos se inician juntos en la nueva EC2. El broker anterior y su volumen anónimo quedan intactos hasta validar el nuevo clúster; antes de redirigir productores y consumidores, comprobar que sus colas no tienen mensajes pendientes. Un clúster de dos nodos no replica automáticamente los mensajes de las colas clásicas. Los dos nodos en una misma EC2 tampoco toleran la caída de esa máquina.
4. `catalogo-api` y `carrito-api` de la EC2 antigua deben reconstruirse con sus productores Kafka y apuntar a los brokers de la EC2 nueva. `orders-service` debe consultar `ms-pago` en la red privada. `ms-pago` consulta la MariaDB antigua.
5. API Gateway necesita rutas nuevas para pagos/analítica. El frontend HTTPS necesita un proxy WebSocket hacia la EC2 nueva, con configuración Nginx revisada. La URL de retorno de Webpay debe seguir apuntando al frontend actual.

## Puertos entre instancias

Crear un grupo de seguridad propio para `pedidos360-platform`. Autorizar únicamente el origen indicado; no usar `0.0.0.0/0` para brokers, base de datos o servicios internos.

| Destino | Puertos TCP | Origen permitido | Motivo |
| --- | --- | --- | --- |
| EC2 nueva | `29092-29094` | Grupo de seguridad de EC2 actual | Productores Kafka de catálogo/carrito. Los brokers anuncian la IP privada de la nueva EC2. |
| EC2 nueva | `5672` | Grupo de seguridad de EC2 actual | Productores y consumidores RabbitMQ. |
| EC2 nueva | `8087-8088` | Grupo de seguridad de EC2 actual | Verificación de pagos y proxy de analítica/WebSocket desde Nginx. |
| EC2 actual | `3306` | Grupo de seguridad de EC2 nueva | `ms-pago` usa la MariaDB ya existente. |
| EC2 nueva | `22` | IP pública del estudiante `/32` | Administración SSH o EC2 Instance Connect, según permita el laboratorio. |

Kafka UI y RabbitMQ Management se publican solo en `127.0.0.1` de la EC2 nueva (`8090` y `15672`), para acceder por túnel SSH. Si el laboratorio usa solo EC2 Instance Connect sin túnel, preparar una ruta de administración autenticada antes de exponerlos; no abrir sus puertos a todo Internet. Los brokers, ZooKeeper y RabbitMQ segundo nodo se comunican dentro de la red Docker de la EC2 nueva, sin abrir sus puertos de clúster en AWS.

## Orden de activación

1. Lanzar nueva `t3.large` en la misma VPC/subred y comprobar `StartInstances --dry-run`; registrar IP privada, sistema operativo y espacio libre. La prueba del usuario dio `DryRunOperation` para `RunInstances` y `StartInstances` de `i-05a83d666c15001df`.
2. Instalar Docker/Compose y clonar la rama `main` una vez que los archivos nuevos estén revisados y subidos. Crear `.env` local a partir de `.env.example`, con valores existentes de MariaDB y RabbitMQ sin publicarlos.
3. Iniciar primero ZooKeeper y Kafka; comprobar tres brokers y crear topics con `KAFKA_DEPLOYMENT=platform bash scripts/kafka-topics.sh create`. Después iniciar RabbitMQ y comprobar `rabbitmqctl cluster_status`.
4. Abrir reglas privadas y probar conectividad desde ambas EC2. Iniciar analítica y pagos con sus dependencias. Crear tabla/rol DynamoDB antes de activar `DYNAMODB_ENABLED=true`.
5. Confirmar que el Rabbit anterior no guarda mensajes pendientes. Reconstruir y cambiar los servicios de la EC2 actual con `docker-compose.core-ec2.yml`; aplicar políticas DLQ con `RABBIT_DEPLOYMENT=platform bash scripts/configure-rabbit-dlq.sh` después de que órdenes declare exchanges y colas.
6. Agregar proxy HTTPS/WebSocket en el Nginx de la EC2 actual y nuevas rutas HTTP del API Gateway. Activar flags del frontend y probar compra, cola, pago, Kafka, DynamoDB y métricas.

Cada etapa tiene que funcionar antes de la siguiente; el código en Git por sí solo no demuestra operación en AWS.

## Decisión práctica

**Dos EC2 conservan la URL actual, pero exigen adaptar el despliegue a dos hosts.** `docker-compose.platform-ec2.yml` define los componentes nuevos y limita su memoria; `docker-compose.core-ec2.yml` conecta los servicios existentes después de validar la plataforma. Ambos archivos pasaron `docker compose config --quiet`, pero no se han probado con contenedores. El overlay anterior `docker-compose.streaming.yml` sigue siendo solo para una EC2 única. No activar las tres configuraciones a la vez.

Antes de crearla, anota de la EC2 actual: VPC ID, subnet ID, security group ID, IP privada y par de claves de acceso. No publiques la cookie Erlang ni contraseñas. Después de crearla, anota Instance ID e IP privada y ejecuta `aws ec2 start-instances --dry-run --instance-ids ID_NUEVO --region us-east-1` en CloudShell: `DryRunOperation` indica permiso para iniciar y `UnauthorizedOperation` indica bloqueo. Esa prueba **no** detiene la instancia.

Referencias: [direcciones IP de EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-instance-addressing.html), [reglas de security groups](https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html), [clúster RabbitMQ](https://www.rabbitmq.com/docs/clustering).
