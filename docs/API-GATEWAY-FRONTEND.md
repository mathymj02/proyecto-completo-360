# Servir Angular mediante el API Gateway existente

**Estado 26-11-2026:** la HTTP API `d4ehy77hhb` en `us-east-1` tiene la ruta `$default` (`5gbrcmm`) sin autorizador, conectada a la integración HTTP_PROXY `ieueaxa` de `http://100.48.154.224`. El stage `$default` despliega cambios automáticamente. Se verificó `200` para `/` y `/dashboard` a través del gateway y `401` sin token para la ruta protegida del catálogo. Se agregó el origen HTTPS del gateway a CORS y se conservaron los orígenes HTTPS de la EC2 y HTTP de desarrollo local. El preflight de las rutas API sin ruta `OPTIONS` explícita recibió `405` desde Nginx; la respuesta `204` queda preparada en esta configuración y pendiente de despliegue y verificación.

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

El gateway público accede al puerto 80 de la IP pública de la EC2 original; la IP privada `172.31.41.113` solo se usa entre las EC2. Si cambia la IP pública, actualizar la URI de la integración y los orígenes CORS que correspondan.

## Preparar Nginx sin reconstruir Angular

El `301` del puerto 80 impide usarlo directamente como origen de la ruta: el navegador abandonaría la URL de API Gateway. La configuración versionada [`frontend/nginx.gateway.conf`](../frontend/nginx.gateway.conf) conserva el HTTPS directo y la redirección HTTP para solicitudes normales. Cuando API Gateway entrega una solicitud cuyo origen era HTTPS, añade la cabecera estándar `Forwarded` con `proto=https`; en ese caso, el servidor HTTP entrega Angular. El overlay [`docker-compose.frontend-gateway-ec2.yml`](../docker-compose.frontend-gateway-ec2.yml) monta solo esa configuración sobre el contenedor Nginx existente; no recompila el frontend ni reemplaza el certificado.

En la EC2 original, desde `/home/ec2-user/proyecto-completo-360-core`, probar la configuración **antes** de recrear el contenedor:

```bash
docker run --rm --entrypoint nginx -v "$PWD/frontend/nginx.gateway.conf:/etc/nginx/conf.d/default.conf:ro" proyecto-completo-frontend:latest -t
```

Si pasa, recrear solo `frontend` con el overlay:

```bash
docker compose -p proyecto-completo -f docker-compose.live-ec2.yml -f docker-compose.frontend-gateway-ec2.yml up -d --no-deps frontend
```

Comprobar `curl -sSI http://127.0.0.1/` (debe devolver `301`), `curl -sSI -H 'Forwarded: proto=https' http://127.0.0.1/` (debe devolver `200`) y `curl -ksSI https://127.0.0.1/` (debe devolver `200`). Un `OPTIONS` con la cabecera `Forwarded: proto=https` debe devolver `204`; API Gateway agrega las cabeceras CORS configuradas. Si falla, repetir `up -d --no-deps frontend` sin el overlay para restaurar la configuración anterior.

La cabecera `Forwarded` distingue recorridos, **no autentica al gateway**: un cliente directo puede enviarla. Además, API Gateway se comunica por HTTP con la IP pública de EC2. Esta solución reproduce el requisito del laboratorio; para un despliegue de producción, usar una integración privada mediante VPC Link y un balanceador o un origen HTTPS con certificado válido, además de restringir el acceso al origen.

## Ruta e integración creadas

La integración HTTP_PROXY apunta a `http://100.48.154.224:80` y la ruta `$default` apunta a `integrations/ieueaxa`. `$default` no tiene Lambda Authorizer; las rutas de negocio `/api/v1/...` conservan sus autorizadores y targets. La consola de esta HTTP API no ofrecía `$default` en el menú de métodos, por lo que se creó con `aws apigatewayv2 create-route --route-key '$default'` desde CloudShell. Las comillas simples evitan que Bash interprete `$default` como variable.

Para demostrar el resultado, consultar la URL raíz del gateway y una ruta de Angular recargada directamente. Deben devolver HTML del frontend. Consultar una ruta API explícita sin token y con token válido: la primera debe ser rechazada por autorización y la segunda debe ir al microservicio. Si una ruta API devuelve HTML, falta una ruta o está mal conectada. La URL de `environment.prod.ts` ya señala `d4ehy77hhb` para las API principales.

## Entra ID y CORS

MSAL usa `${window.location.origin}/` como URI de retorno. Para abrir el frontend desde el gateway, registrar **exactamente** `https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com/` como redirect URI de tipo SPA en el registro de aplicación de Entra ID, además de la URL HTTPS de la EC2 usada actualmente. Comprobar login y logout desde ambas URL.

El origen para CORS es solo esquema + host + puerto opcional, sin `/` final. La configuración actual contiene `https://100.48.154.224`, `http://localhost:4200` y `https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com` como entradas **separadas**, además de `GET, POST, PUT, DELETE, OPTIONS`, `authorization, content-type`, credenciales y `MaxAge=300`. Si la página se abre desde el gateway y las API también usan ese mismo origen, no hay solicitud de origen cruzado entre ambos. La URL directa de la EC2 sí cruza al gateway. Como `$default` reenvía a Nginx los `OPTIONS` de rutas API que no tienen ruta `OPTIONS` propia, Nginx debe contestar `204`; las cabeceras CORS que añade API Gateway por sí solas no convierten un `405` en un preflight válido. Verificar después del despliegue con una petición `OPTIONS` que incluya `Origin` y `Access-Control-Request-Method`.

Referencias: [selección de rutas de HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-routes.html), [integraciones HTTP](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-http.html), [cabeceras Forwarded de HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-known-issues.html), [CORS en HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html).
