# Servir Angular mediante el API Gateway existente

**Estado 19-11-2026:** procedimiento preparado; la ruta hacia el frontend aún no se ha creado ni probado en AWS. El gateway de la demostración es la HTTP API `d4ehy77hhb` en `us-east-1`. Se comprobó que su stage `$default` tiene despliegue automático y que no existe una ruta `$default`. El frontend existente se sirve desde la EC2 original `100.48.154.224`; su Nginx actual redirige HTTP `80` a HTTPS `443` con un `301`. El clúster de mensajería está en otra EC2. No cambiar las integraciones de las API durante esta operación.

## Cómo queda el recorrido

```text
Navegador -> https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com/
          -> ruta $default (sin autorizador) -> HTTP_PROXY de Nginx :80
          -> index.html, JavaScript, CSS y rutas de Angular

Angular -> rutas explícitas /api/v1/... del mismo gateway
        -> autorizador Lambda existente -> microservicio correspondiente
```

En una HTTP API las rutas explícitas tienen prioridad sobre `$default`. Hay que comprobar que todas las rutas API necesarias estén declaradas explícitamente antes de agregar `$default`: de lo contrario, una petición faltante recibirá `index.html` desde Nginx. La ruta del sitio es pública; el autorizador se conserva en las rutas de negocio. Una HTTP API usa su integración HTTP para reenviar peticiones, pero no es un proxy WebSocket de este servicio. Las métricas WebSocket se mantienen en el Nginx HTTPS de la EC2 original, con conexión privada al servicio de analítica.

## Inventario en CloudShell

Ejecutar en **CloudShell**, una orden por línea. Estas consultas son de solo lectura:

```bash
aws apigatewayv2 get-stages --api-id d4ehy77hhb --region us-east-1 --query 'Items[*].[StageName,AutoDeploy]' --output table
aws apigatewayv2 get-routes --api-id d4ehy77hhb --region us-east-1 --query 'Items[*].[RouteKey,AuthorizationType,Target]' --output table
aws apigatewayv2 get-integrations --api-id d4ehy77hhb --region us-east-1 --query 'Items[*].[IntegrationId,IntegrationType,IntegrationUri]' --output table
aws apigatewayv2 get-api --api-id d4ehy77hhb --region us-east-1 --query '[ApiEndpoint,CorsConfiguration.AllowOrigins,CorsConfiguration.AllowMethods,CorsConfiguration.AllowHeaders]' --output json
```

El stage es `$default`, `AutoDeploy` está activo y no había ruta `$default` al revisar la consola. Verificar el puerto 80 de Nginx y la regla de entrada de la EC2 original antes de crear la integración. El gateway público accede a esa IP pública; la IP privada `172.31.41.113` solo se usa entre las EC2.

## Preparar Nginx sin reconstruir Angular

El `301` del puerto 80 impide usarlo directamente como origen de la ruta: el navegador abandonaría la URL de API Gateway. La configuración versionada [`frontend/nginx.gateway.conf`](../frontend/nginx.gateway.conf) conserva el HTTPS directo y la redirección HTTP para solicitudes normales. Cuando API Gateway entrega una solicitud cuyo origen era HTTPS, añade la cabecera estándar `Forwarded` con `proto=https`; en ese caso, el servidor HTTP entrega Angular. El overlay [`docker-compose.frontend-gateway-ec2.yml`](../docker-compose.frontend-gateway-ec2.yml) monta solo esa configuración sobre el contenedor Nginx existente; no recompila el frontend ni reemplaza el certificado.

En la EC2 original, desde `/home/ec2-user/proyecto-completo-360-core`, probar la configuración **antes** de recrear el contenedor:

```bash
docker run --rm -v "$PWD/frontend/nginx.gateway.conf:/etc/nginx/conf.d/default.conf:ro" proyecto-completo-frontend:latest nginx -t
```

Si pasa, recrear solo `frontend` con el overlay. Comprobar `curl -sSI http://127.0.0.1/` (debe devolver `301`), `curl -sSI -H 'Forwarded: proto=https' http://127.0.0.1/` (debe devolver `200`) y `curl -ksSI https://127.0.0.1/` (debe devolver `200`). Si falla, repetir `up -d --no-deps frontend` sin el overlay para restaurar la configuración anterior. Confirmar que el navegador sigue abriendo `https://100.48.154.224` antes de crear la ruta en AWS.

La cabecera `Forwarded` distingue recorridos, **no autentica al gateway**: un cliente directo puede enviarla. Además, API Gateway se comunica por HTTP con la IP pública de EC2. Esta solución reproduce el requisito del laboratorio; para un despliegue de producción, usar una integración privada mediante VPC Link y un balanceador o un origen HTTPS con certificado válido, además de restringir el acceso al origen.

## Cambio propuesto, después del inventario

Crear una integración HTTP_PROXY hacia `http://100.48.154.224:80` y conectar la ruta `$default` a su ID. No adjuntar Lambda Authorizer a `$default`. Conservar los targets y autorizadores actuales de `/api/v1/...`. La integración base debe permitir que Nginx resuelva tanto `/` como los archivos de Angular y las rutas de la SPA. Usar la consola de API Gateway o los comandos `create-integration` y `create-route` con los IDs comprobados en el inventario; evitar copiar identificadores de otro entorno.

Para demostrar el resultado, consultar la URL raíz del gateway y una ruta de Angular recargada directamente. Deben devolver HTML del frontend. Consultar una ruta API explícita sin token y con token válido: la primera debe ser rechazada por autorización y la segunda debe ir al microservicio. Si una ruta API devuelve HTML, falta una ruta o está mal conectada. La URL de `environment.prod.ts` ya señala `d4ehy77hhb` para las API principales.

## Entra ID y CORS

MSAL usa `${window.location.origin}/` como URI de retorno. Para abrir el frontend desde el gateway, registrar **exactamente** `https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com/` como redirect URI de tipo SPA en el registro de aplicación de Entra ID, además de la URL HTTPS de la EC2 usada actualmente. Comprobar login y logout desde ambas URL.

El origen para CORS es solo esquema + host + puerto opcional, sin `/` final. Si la página se abre desde el gateway y las API también usan ese mismo origen, no hay solicitud de origen cruzado entre ambos. Conservar `https://100.48.154.224` y `http://localhost:4200` como entradas **separadas** si se siguen usando esas páginas para llamar al gateway. No pegar varios orígenes separados por comas en una sola entrada. Verificar `GET, POST, PUT, DELETE, OPTIONS` y encabezados `authorization, content-type`; volver a probar el preflight tras guardar los cambios.

Referencias: [selección de rutas de HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-routes.html), [integraciones HTTP](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-http.html), [cabeceras Forwarded de HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-known-issues.html), [CORS en HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html).
