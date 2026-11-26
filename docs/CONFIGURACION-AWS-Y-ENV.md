# Configuración de AWS y variables de entorno

Esta guía distingue el código versionado de los recursos que se crean en AWS. Usa `.env.example` como plantilla: copia el archivo a `.env`, reemplaza los valores de ejemplo y conserva `.env` únicamente en tu equipo o EC2. Git ignora `.env`. Una variable en Compose pasa al contenedor mediante `environment`; `application.yml` de Spring la lee con `${NOMBRE}`. Cambiar `.env` requiere recrear el contenedor correspondiente.

## De dónde sale cada valor

| Variable | Dónde se obtiene | Para qué sirve |
| --- | --- | --- |
| `RABBIT_USER`, `RABBIT_PASSWORD` | Los eliges tú al instalar RabbitMQ; deben coincidir con el usuario existente del broker. | Autenticación AMQP y panel web. **No** provienen de AWS Details. |
| `RABBIT_ERLANG_COOKIE` | Genera un valor aleatorio largo y usa el mismo en ambos nodos RabbitMQ. | Permite que los nodos se reconozcan. |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` | Los eliges al crear MariaDB en Compose. Si usas RDS, toma host/puerto en **RDS → Databases → Connectivity & security** y el usuario configurado al crear la base. | Conexión SQL. Cambiar la contraseña en `.env` no cambia automáticamente la de una base ya inicializada. |
| `PAYMENTS_DB_NAME`, `PAYMENTS_DB_USERNAME`, `PAYMENTS_DB_PASSWORD`, `PAYMENTS_DB_ROOT_PASSWORD` | Base MariaDB exclusiva de `ms-pago` en la EC2 nueva; `scripts/init-payment-env.py` genera las contraseñas localmente. | El servicio de pagos no necesita las credenciales ni la conexión de la MariaDB antigua. No cambiar las contraseñas después de inicializar el volumen sin rotarlas también dentro de la base. |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Cuenta y clave de aplicación del servidor SMTP usado por `notifications-service`, si se activa correo real. | Se pasan como variables de entorno; quedan vacías si no se usa SMTP. Si alguna clave estuvo publicada en GitHub, hay que revocarla y generar otra. |
| `AWS_REGION` | Selector de región en la consola AWS, por ejemplo `us-east-1`. | Región usada por el SDK de DynamoDB. |
| `DYNAMODB_TABLE` | **DynamoDB → Tables → nombre de la tabla**. | Tabla para eventos analíticos. |
| `DYNAMODB_ENABLED` | `false` hasta crear tabla y permisos; después `true`. | Activa la escritura en DynamoDB. |
| `FRONTEND_ORIGIN` | Barra del navegador: protocolo, host y puerto opcional, sin ruta ni `/` final. | Origen permitido para CORS del servicio de analítica. |
| `FRONTEND_BASE_URL` | URL pública donde vive Angular; sin `/` final. | URL de retorno de Webpay. Debe abrir Angular y su ruta `/payment-return/:id`. |
| `CARD_HASH_SALT` | Genera un valor aleatorio local. | Hash del alias de tarjeta usado en la **simulación** antifraude. No se almacena el número real de tarjeta. |
| `WEBPAY_COMMERCE_CODE`, `WEBPAY_API_KEY` | Documentación oficial de Transbank para **ambiente de integración**, o credenciales privadas de un comercio si se pasa a producción. | Peticiones servidor a servidor a Webpay Plus. Nunca copies claves productivas al repositorio. |

Para generar una cookie y sal en Linux: `openssl rand -hex 32` (ejecútalo dos veces y usa valores distintos). No pegues la salida en un chat ni en un commit. Si el broker o MariaDB ya tienen datos, confirma las credenciales actuales antes de recrearlos.

En la EC2 nueva, `python3 scripts/init-payment-env.py` rellena únicamente los valores de ejemplo o ausentes en el `.env` local. Genera contraseñas y sal aleatorias, configura la URL del Gateway usada como retorno de Webpay y coloca las **credenciales públicas de integración** de Transbank. El script no muestra los valores. Las credenciales de producción se configuran por separado y nunca se versionan. Para la demostración de pago, abrir Angular desde el Gateway: la compra pendiente está en el `sessionStorage` de ese origen y Webpay debe volver allí.

## Credenciales AWS y DynamoDB

En EC2, asocia un **IAM instance profile** con permisos mínimos sobre la tabla: `dynamodb:PutItem` y `dynamodb:DescribeTable`. El SDK usa la cadena de credenciales predeterminada y recibe credenciales temporales del rol. No hace falta colocar `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` en `.env`. En un equipo local se puede usar un perfil de AWS CLI con permisos equivalentes. El ejemplo usa una tabla con clave de partición `eventId` de tipo String y capacidad bajo demanda. El código fuente prepara esta integración; hasta verificar rol, tabla y eventos escritos en AWS, no afirmar que DynamoDB funciona en producción.

## API Gateway, Lambda y CORS

El ID `d4ehy77hhb` corresponde a la HTTP API observada en `us-east-1`. En **API Gateway → APIs → tu API → Routes** se ven métodos y rutas; en **Integrations** se ve la URL HTTP real de cada microservicio. `POST /api/v1/orders` apuntaba a `http://100.48.154.224:8084/api/v1/orders`. En **Stages** se ve la URL de invocación. La ruta pública `$default` ya entrega Angular desde Nginx; `/` y `/dashboard` devolvieron `200` y se comprobó el inicio de sesión desde esa URL. Las rutas de negocio explícitas siguen teniendo su autorizador.

En **API Gateway → CORS**, cada origen es una entrada separada. En esta demostración constan `https://100.48.154.224`, `https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com` y `http://localhost:4200`. La entrada contiene solo origen, sin comas, ruta o barra final. Se permiten `authorization`, `content-type` y los métodos usados; el preflight devolvió `204`. CORS permite peticiones del navegador; la validación JWT la hace el authorizer y/o cada API.

La Lambda `pedidos360-authorizer` está fuera de Compose. Su fuente vive en `backend/authorizer-lambda`, pero corregir ese archivo en Git no actualiza por sí solo la función AWS: hay que compilar, publicar el artefacto y probar el token válido, vencido y con firma incorrecta. Configura `AZURE_TENANT_ID` y `AZURE_CLIENT_ID` de **Microsoft Entra ID → App registrations → Overview** y revisa issuer/audience de los access tokens. La URL de redirección de la aplicación SPA se configura en **App registrations → Authentication** y debe coincidir con la URL pública real de Angular.

## Qué pasa al ampliar la EC2

Los 20 GiB que se ampliaron fueron **disco**, no RAM. El `free -h` aportado mostró 913 MiB de RAM y 70 MiB disponibles; esa instancia no puede alojar de forma fiable tres ZooKeeper, tres Kafka y los servicios Java actuales. Hay que cambiar el tipo de instancia o repartir los brokers en más instancias. Antes de detenerla, identifica su **instance ID**, volúmenes y si tiene **Elastic IP**. Si usa IP pública automática, al detener e iniciar puede cambiar. En tal caso actualiza integraciones de API Gateway, CORS, URL de retorno Webpay, redirección Entra y cualquier URL compilada en Angular. Verifica `free -h`, `df -h`, `docker compose ps` y el acceso al frontend después del cambio.

No actives `docker-compose.rabbit-cluster.yml` directamente sobre el RabbitMQ actual: el primer nodo usa un volumen nuevo mientras la EC2 previa tenía un volumen anónimo. Hay que identificar y migrar los datos existentes, o respaldar y recrear conscientemente el broker. Dos nodos cumplen la topología pedida por la rúbrica, pero no dan por sí solos tolerancia completa a particiones.

## Comprobación sin revelar secretos

Desde la raíz del proyecto, `docker compose config --quiet` valida la estructura de Compose; `docker compose ps` muestra los contenedores; `docker compose logs --since=10m orders-service catalogo-api rabbitmq` ayuda a seguir una orden. **No** compartas `docker compose config` completo ni el contenido de `.env`: ambos pueden mostrar contraseñas.

Fuentes: [IAM roles for EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html), [credenciales del SDK Java](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html), [cambiar tipo de EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/change-instance-type-of-ebs-backed-instance.html), [CORS de HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html), [Webpay Plus](https://github.com/TransbankDevelopers/transbank-developers-docs/blob/master/documentacion/webpay-plus/README.md).
