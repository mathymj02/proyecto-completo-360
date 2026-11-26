# Pedidos360

Aplicación de pedidos para estudiar arquitectura cloud y DevOps. Combina un frontend Angular, servicios Spring Boot, identidad Microsoft Entra ID, Amazon API Gateway, RabbitMQ, Kafka, WebSocket y DynamoDB. El repositorio contiene código, configuraciones Docker Compose y guías; API Gateway, IAM, grupos de seguridad y la tabla DynamoDB se administran en AWS.

> **Estado documentado: 26 de noviembre de 2026.** El flujo de compra y la recuperación de la cola de stock se probaron en la EC2 original. En la segunda EC2 funcionan tres brokers Kafka, dos nodos RabbitMQ en clúster, Kafka UI y analítica. Una acción real «Agregar al Carrito» publicó un evento Kafka que analítica consumió (`cartAdds: 1`); un evento sintético de pago elevó `paymentAttempts` y `fraudAlerts`. Ese último evento se guardó en la tabla DynamoDB `Pedidos360Events`, comprobada con `Count: 1`. El frontend cargó e inició sesión mediante la ruta `$default` de API Gateway; CORS devolvió `204`. El proxy de analítica respondió `200` y el WebSocket aceptó una conexión `101` y envió métricas. `ms-pago` arrancó con una MariaDB propia, pero una transacción Webpay de integración todavía no se ha demostrado. Tampoco se han migrado los consumidores al RabbitMQ nuevo. Consulta [avance de la evaluación](docs/AVANCE-EVALUACION.md) y [configuración AWS y `.env`](docs/CONFIGURACION-AWS-Y-ENV.md).

Para continuar el despliegue de dos instancias, sigue [la guía de dos EC2](docs/OPCION-DOS-EC2.md). La alternativa de [cambiar el tipo de la EC2 original](docs/SIGUIENTE-PASO-EC2.md) quedó bloqueada por la política del laboratorio.

Para presentar el sistema, usa el [guion breve de defensa](docs/DEFENSA-EVALUACION.md): muestra evidencias observadas y explica los límites de cada prueba.

## Qué hace el sistema

1. El usuario abre Angular, inicia sesión en Microsoft Entra ID y recibe tokens mediante MSAL.
2. Consulta el catálogo, administra su carrito y confirma una compra.
3. Angular envía la orden a través de API Gateway. El servicio de órdenes asigna un ID y publica un evento en RabbitMQ.
4. Tres colas independientes entregan el evento a catálogo, notificaciones y envíos. Catálogo descuenta existencias; envíos genera una guía; notificaciones intenta enviar un correo.

La respuesta HTTP `201 Created` de la compra confirma que el servicio publicó el evento y recibió la confirmación del broker. **No confirma que todos los consumidores hayan terminado**. Esa diferencia es central para entender el sistema asíncrono.

## Arquitectura

```mermaid
flowchart LR
    U[Usuario / navegador] --> F[Angular + MSAL<br/>Nginx en EC2]
    F <-->|OAuth 2.0 / OIDC| E[Microsoft Entra ID<br/>recurso externo]
    F -->|HTTPS + Bearer token| G[Amazon API Gateway<br/>HTTP API + CORS]
    G --> L[Lambda Authorizer<br/>configuración en AWS]
    G -->|Integraciones HTTP| S[Servicios Spring Boot<br/>Docker Compose en EC2]
    S --> DB[(Base de datos SQL)]
    S --> O[orders-service]
    O -->|orden.creada| X{RabbitMQ<br/>pedidos_exchange}
    X --> Q1[(stock_queue)]
    X --> Q2[(notifications_queue)]
    X --> Q3[(envios_queue)]
    Q1 --> C[catalogo-api]
    Q2 --> N[notifications-service]
    Q3 --> V[envios-service]
    N -.-> SMTP[Servidor SMTP externo]
```

El diagrama muestra el flujo de compra heredado en la EC2 original. Microsoft Entra ID, API Gateway y Lambda no se crean con `docker-compose.yml`. La base de datos también cambia según el entorno: algunos archivos versionados apuntan a una instancia RDS anterior; la EC2 de la demostración usa MariaDB mediante una modificación local de Compose. La nueva plataforma de Kafka, analítica y pagos vive en una segunda EC2, descrita más abajo.

### Componentes

| Componente | Función | Código o configuración versionada |
| --- | --- | --- |
| Angular 18 + MSAL | Inicio de sesión, catálogo, carrito y compra | [`frontend/`](frontend/), [`auth-config.ts`](frontend/src/app/config/auth-config.ts) |
| `auth-api` · 8083 | Endpoint de sesión y validación de tokens de Entra ID | [`backend/auth-api/`](backend/auth-api/) |
| `catalogo-api` · 8081 | CRUD de productos y consumidor que actualiza stock | [`backend/catalogo-api/`](backend/catalogo-api/) |
| `carrito-api` · 8082 | Carrito asociado a la identidad del usuario | [`backend/carrito-api/`](backend/carrito-api/) |
| `orders-service` · 8084 | Valida la solicitud, crea ID de orden y publica el evento | [`backend/orders-service/`](backend/orders-service/) |
| `notifications-service` · 8085 | Consume la orden e intenta notificar por correo | [`backend/notifications-service/`](backend/notifications-service/) |
| `envios-service` · 8086 | Consume la orden y genera una guía de despacho | [`backend/envios-service/`](backend/envios-service/) |
| RabbitMQ | Exchange, colas y entrega de mensajes AMQP | [`RabbitMQConfig.java`](backend/orders-service/src/main/java/com/pedidos360/orders/config/RabbitMQConfig.java), [`docker-compose.yml`](docker-compose.yml) |
| API Gateway + Lambda | Rutas públicas/protegidas y autorización previa | Configuración en AWS; hay un proyecto de código fuente en [`backend/authorizer-lambda/`](backend/authorizer-lambda/) |
| Kafka y ZooKeeper | Tres brokers y tres nodos de coordinación; topics replicados para navegación e intentos de pago | [`docker-compose.platform-ec2.yml`](docker-compose.platform-ec2.yml), [`scripts/kafka-topics.sh`](scripts/kafka-topics.sh) |
| `analytics-recommendations` · 8088 | Consume Kafka, agrega métricas, recomienda productos y emite WebSocket | [`backend/analytics-recommendations/`](backend/analytics-recommendations/), [guía de métricas](docs/METRICAS-WEBSOCKET.md) |
| DynamoDB | Guarda los eventos analíticos por `eventId` mediante el rol temporal de la EC2 | Cliente en [`DynamoEventStore.java`](backend/analytics-recommendations/src/main/java/com/pedidos360/analytics/service/DynamoEventStore.java); tabla e IAM en AWS |
| `ms-pago` · 8087 | Inicia y confirma Webpay de integración; registra intentos en MariaDB propia y publica eventos Kafka | [`backend/ms-pago/`](backend/ms-pago/), [guía de pagos](docs/PAGOS-WEBPAY.md) |

## Identidad y autorización: OAuth 2.0, OIDC y MSAL

**OAuth 2.0** permite que el navegador obtenga un *access token* para llamar a una API con permisos delegados. **OpenID Connect (OIDC)** agrega la identidad del usuario y el *ID token*. **MSAL Angular** es la biblioteca cliente que inicia la sesión, solicita los scopes configurados y administra los tokens. El flujo para aplicaciones de página única es código de autorización con PKCE, según la [documentación de Microsoft](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow).

En este proyecto, [`auth-config.ts`](frontend/src/app/config/auth-config.ts) crea `PublicClientApplication`, usa redirección para iniciar sesión, guarda la sesión en `sessionStorage` y configura `MsalInterceptor` para agregar `Authorization: Bearer <access token>` a las peticiones de las URL protegidas. [`app.routes.ts`](frontend/src/app/app.routes.ts) protege `/dashboard` con `MsalGuard` y un `roleGuard` propio. El segundo lee claims del **ID token** y deja pasar cuando no hay permisos presentes; es una ayuda de interfaz y **no una garantía de autorización**.

Las API de autenticación, catálogo y carrito contienen configuración de Spring Security para validar JWT frente a las claves públicas de Entra ID, además de issuer, audience y tiempo de validez. El *access token* es el que debe autorizar el acceso a la API; el *ID token* identifica la sesión del cliente. Las URL de redirección, identificadores de aplicación, audiencia y scopes se deben registrar de forma coherente en Entra ID y en [`frontend/src/environments/`](frontend/src/environments/). Para los conceptos de protocolo, ver [Microsoft Entra ID](https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols) y [MSAL Angular](https://learn.microsoft.com/en-us/entra/msal/javascript/angular/msal-interceptor).

### Lambda Authorizer: estado real

La API HTTP de AWS tiene un Lambda Authorizer asociado a rutas como `POST /api/v1/orders`. API Gateway toma el encabezado `Authorization`, invoca Lambda y decide si continúa hacia la integración HTTP. Se configuró respuesta simple con formato de payload `2.0`. La [documentación de AWS](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-lambda-authorizer.html) describe ese contrato.

La fuente anterior de [`AuthorizerHandler.java`](backend/authorizer-lambda/src/main/java/com/pedidos360/authorizer/AuthorizerHandler.java) contenía comillas escapadas literales (`\"`) fuera de una cadena Java: por eso no compilaba. La versión en `main` usa comillas Java normales y Nimbus para verificar firma RS256 mediante JWKS, issuer, audience y vigencia; compiló localmente. **Esto todavía no demuestra que la Lambda desplegada en AWS use ese artefacto.** Hay que publicarlo y probar tokens válidos e inválidos. El servicio de órdenes depende de la protección de la ruta en AWS y no debe exponerse directamente sin una verificación equivalente.

## Enrutamiento en API Gateway y CORS

El frontend consume la **URL pública de API Gateway** para sus llamadas HTTP. El gateway enruta cada método y ruta, por ejemplo `POST /api/v1/orders`, a una integración HTTP que apunta al servicio correspondiente en EC2. Una ruta pública `$default` sirve Angular desde Nginx; las rutas API explícitas conservan su autorización. Se observaron `200` en `/` y `/dashboard` a través del gateway, `401` en catálogo sin token e inicio de sesión exitoso desde esa URL. El recorrido está en [API Gateway para el frontend](docs/API-GATEWAY-FRONTEND.md).

Durante la demostración se observó el gateway `d4ehy77hhb` en `us-east-1`, con `POST /api/v1/orders` integrado a `http://100.48.154.224:8084/api/v1/orders`. El frontend se sirvió desde `https://100.48.154.224`. Estos datos son del entorno de evaluación, no una garantía de disponibilidad futura; una IP pública de EC2 puede cambiar. El [`environment.prod.ts`](frontend/src/environments/environment.prod.ts) de `main` ya apunta a `d4ehy77hhb` para las API principales; la EC2 original conserva ajustes locales de Nginx/HTTPS. Al reproducir el despliegue hay que mantener una sola configuración de gateway y frontend coherente.

**CORS** es la política que permite al navegador llamar a un origen diferente. La HTTP API tiene como orígenes separados `https://100.48.154.224`, `https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com` y `http://localhost:4200`; cada entrada contiene solo esquema, host y puerto opcional, sin rutas ni varias URL en un mismo campo. Los métodos incluyen `GET`, `POST`, `PUT`, `DELETE` y `OPTIONS`, y los encabezados `authorization` y `content-type`. El preflight `OPTIONS` devolvió `204` con las cabeceras esperadas. CORS **no autentica** al usuario: la autorización sigue dependiendo del token y de las verificaciones del backend/authorizer. En HTTP API, la [configuración CORS de AWS](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html) puede responder automáticamente al preflight e ignorar las cabeceras CORS de la integración.

| Método y ruta principal | Responsable | Uso |
| --- | --- | --- |
| `GET /api/v1/auth/public/ping`, `GET /api/v1/auth/me` | `auth-api` | Estado y datos del usuario |
| `GET/POST /api/v1/catalogo/productos`, `GET/PUT/DELETE /api/v1/catalogo/productos/{id}` | `catalogo-api` | Consulta y administración del catálogo |
| `GET/DELETE /api/v1/carritos/mios`, `POST /api/v1/carritos/items`, `PUT/DELETE /api/v1/carritos/items/{itemId}` | `carrito-api` | Carrito de compra |
| `POST /api/v1/orders` | `orders-service` | Publicar una compra |
| `POST /api/v1/notifications/send` | `notifications-service` | Envío solicitado directamente |
| `GET /api/v1/envios`, `GET /api/v1/envios/{orderId}` | `envios-service` | Consultar guías en memoria |

Esta tabla enumera endpoints implementados en controladores. Una ruta visible en AWS no implica que el controlador correspondiente implemente ese método: por ejemplo, el controlador de órdenes **no** tiene `GET /api/v1/orders`; solo `POST /api/v1/orders` y `GET /api/v1/orders/ping`.

## Mensajería asíncrona con RabbitMQ

[`OrderController.java`](backend/orders-service/src/main/java/com/pedidos360/orders/controller/OrderController.java) valida que haya ítems y correo, genera `ORD-...`, publica JSON con routing key `orden.creada` en el exchange directo durable `pedidos_exchange`, marca el mensaje como persistente y espera hasta cinco segundos la confirmación del broker. [`RabbitMQConfig.java`](backend/orders-service/src/main/java/com/pedidos360/orders/config/RabbitMQConfig.java) declara tres colas durables unidas con la misma routing key. **Cada cola recibe su propia copia** del evento; el consumo de stock no vacía las otras dos.

```text
POST /api/v1/orders → orders-service → pedidos_exchange (orden.creada)
                                      ├→ stock_queue         → catalogo-api → stock SQL
                                      ├→ notifications_queue → notifications-service → correo
                                      └→ envios_queue        → envios-service → guía
```

En la prueba de recuperación se detuvo `catalogo-api`, se efectuó una compra y la consola de RabbitMQ mostró **1 mensaje Ready** en `stock_queue`. Al encender el servicio, la cola bajó a **0** y el log registró `ORD-39429633` y el nuevo stock de “Monitor 24 pulgadas” en **14 unidades**. Esto demuestra la entrega diferida en esa prueba. El consumidor de stock ejecuta la actualización dentro de una transacción y propaga los errores, evitando confirmar como exitosa una actualización fallida.

El panel del RabbitMQ anterior está en el puerto `15672` del host de demostración (`http://100.48.154.224:15672/`), si la instancia y la regla de red están activas. `Ready` significa pendiente de entrega; `Unacked`, entregado y aún sin confirmación del consumidor. La [guía de fiabilidad de RabbitMQ](https://www.rabbitmq.com/docs/reliability) explica confirmaciones y acknowledgements.

Las colas durables y los mensajes persistentes ayudan frente a reinicios del broker, pero no sustituyen una política de respaldo. En la EC2 nueva se verificaron dos nodos en un clúster, tres colas principales, tres DLQ, un exchange directo y uno topic. Mensajes sintéticos rechazados llegaron a las tres DLQ. Los microservicios de la compra todavía consumen el broker anterior; la topología nueva no demuestra por sí sola que una orden real llegue al clúster nuevo. La guía de envíos continúa en memoria y desaparece si se reinicia ese servicio.

## Kafka, analítica, WebSocket y DynamoDB

La EC2 de plataforma ejecuta tres nodos ZooKeeper y tres brokers Kafka. Los topics `navegacion-productos` e `intentos-pago` tienen tres particiones y factor de réplica tres; la comprobación de los brokers devolvió `[1, 2, 3]` y las particiones mostraron las tres réplicas sincronizadas. Una **partición** distribuye los registros de un topic; una **réplica** conserva una copia para tolerar la caída de un broker. Kafka UI permite inspeccionar brokers, topics y offsets. [Guía de despliegue](docs/OPCION-DOS-EC2.md).

```text
carrito-api --ADD_TO_CART--> navegacion-productos --┐
ms-pago ----PAYMENT_ATTEMPT--> intentos-pago ---------┼--> analytics-recommendations
                                                     ├--> métricas REST y WebSocket
                                                     └--> Pedidos360Events (DynamoDB)
```

El consumidor de analítica guarda primero el evento en DynamoDB, después actualiza los contadores en memoria y emite una instantánea por WebSocket. Se observaron eventos reales del carrito y un intento de pago **sintético**. Este último aumentó `paymentAttempts` y `fraudAlerts` y dejó un ítem en DynamoDB. Los contadores en memoria vuelven a cero al reiniciar analítica; DynamoDB conserva los eventos, pero el servicio aún no reconstruye los contadores desde la tabla al arrancar. [Métricas y WebSocket](docs/METRICAS-WEBSOCKET.md).

## Webpay Plus de integración

`ms-pago` usa una MariaDB propia para los intentos. Recibe un JWT de Entra ID, solicita a Webpay un token de pago y envía al navegador a la página de Transbank. Webpay devuelve el control a una ruta del backend que acepta GET o POST, según el resultado; este reenvía a Angular. Solo después de confirmar estado `AUTHORIZED`, monto, orden y código de respuesta se publica la orden en RabbitMQ. Cada transición publica además un evento en Kafka. El antifraude de demostración bloquea el cuarto intento con el mismo **alias** en diez segundos; no recibe el número real de la tarjeta. El botón de pago del frontend sigue desactivado hasta probar una transacción de extremo a extremo en el ambiente de integración. [Flujo y límites](docs/PAGOS-WEBPAY.md).

## Repositorio y despliegue

```text
backend/              Servicios Spring Boot, analítica, pagos y proyecto del authorizer
frontend/             Aplicación Angular, configuración MSAL y Nginx
docker-compose.yml    Topología Docker base
docker-compose.platform-ec2.yml    Clústeres, pagos y analítica en la segunda EC2
scripts/              Preparación, comprobación y operaciones de los servicios
docs/                 Guías de arquitectura, AWS, pagos, métricas y evaluación
amplify.yml           Configuración alternativa de build del frontend
postman/              Colección heredada; no cubre necesariamente las seis API
```

La demostración actual se ejecutó en **Amazon EC2** con Docker Compose: servicios Java, RabbitMQ, base de datos y frontend servido por Nginx. En AWS se configuraron por separado API Gateway, sus rutas, CORS y Lambda Authorizer. `amplify.yml` muestra una opción para compilar el frontend en Amplify, pero el frontend de la prueba se sirvió desde EC2.

La EC2 original conserva cambios locales de HTTPS y certificados de Nginx; su checkout antiguo también contiene ajustes propios que no se deben sobrescribir con un `git pull` indiscriminado. Se usa un clon limpio separado para recibir los cambios de `main` y montar la configuración Nginx versionada. El segundo clon usa `.env` local ignorado para IP privadas, contraseñas de RabbitMQ y MariaDB de pagos, y credenciales públicas de Webpay de integración. `LabInstanceProfile` aporta credenciales temporales AWS a DynamoDB sin guardar claves de acceso en `.env`. Revisa hosts y secretos en la [guía de configuración](docs/CONFIGURACION-AWS-Y-ENV.md). No subir claves privadas TLS, `.env` reales ni contraseñas.

Para inspeccionar la versión en ejecución desde la carpeta del proyecto de la EC2:

```bash
docker compose ps
docker compose logs --since=10m orders-service catalogo-api
docker compose logs --since=10m rabbitmq
```

Para compilar el código localmente se necesitan Java 17, Maven, Node.js compatible con Angular 18 y Docker para los servicios dependientes. El frontend se construye con `npm ci --legacy-peer-deps` y `npm run build` dentro de `frontend/`. La compilación de los microservicios se realiza con Maven en cada módulo. El proyecto `authorizer-lambda` ya compiló tras corregir el defecto descrito arriba, pero su despliegue AWS sigue pendiente. Una colección Postman histórica vive en [`postman/`](postman/); conviene contrastar cada petición con los controladores y el gateway actuales.

## Qué se comprobó y qué falta

| Área | Evidencia o estado actual | Próximo trabajo |
| --- | --- | --- |
| Frontend y catálogo | Cargaron en la EC2 tras reconstruir el frontend y corregir el certificado dentro de la imagen Nginx | Documentar y versionar un despliegue reproducible sin secretos |
| API Gateway y CORS | Ruta `$default` del frontend, login desde Gateway y preflight `204` comprobados; las rutas de negocio mantienen el autorizador | Inventariar las demás rutas, integraciones y orígenes; probar con dominio estable |
| Compra y RabbitMQ | Cola de stock `Ready: 1` con catálogo detenido; `Ready: 0` y stock actualizado al reiniciarlo | Probar fallos, duplicados, persistencia del broker y colas de error |
| Seguridad | MSAL y validación JWT en tres API; authorizer configurado en AWS | Corregir, compilar y auditar Lambda; verificar firma, issuer, audience y permisos; cerrar accesos directos indebidos |
| Operación | Docker Compose en EC2 y base MariaDB configurada localmente | Variables de entorno/secretos, TLS confiable, CI, pruebas automatizadas, observabilidad y backups |
| Nueva evaluación | Kafka, analítica y WebSocket desplegados; evento de pago sintético guardado en DynamoDB; `ms-pago` y MariaDB propia arrancaron | Probar Webpay de extremo a extremo, migrar consumidores RabbitMQ y capturar evidencia visual según [avance](docs/AVANCE-EVALUACION.md) |

Este README separa **código versionado**, **configuración externa observada** y **trabajo pendiente**. El objetivo es que el proyecto sirva tanto para explicar el flujo en una evaluación como para presentar un portafolio técnico verificable, sin atribuir al repositorio capacidades que todavía dependen de una configuración manual o de una prueba no realizada.
