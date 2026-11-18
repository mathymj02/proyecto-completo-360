# Servir Angular mediante el API Gateway existente

**Estado:** procedimiento preparado; la ruta hacia el frontend aún no se ha creado ni probado en AWS. El gateway de la demostración es la HTTP API `d4ehy77hhb` en `us-east-1`. El frontend existente se sirve desde la EC2 original `100.48.154.224`; el clúster de mensajería se está preparando en otra EC2. No cambiar las integraciones de las API durante esta operación.

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

Registrar especialmente el nombre del stage y si `AutoDeploy` está activo. Si ya existe `$default`, revisar su integración y autorización antes de modificarlo. Si el stage no se despliega automáticamente, crear un deployment después del cambio y asociarlo al stage correspondiente. Verificar también el puerto 80 de Nginx y la regla de entrada de la EC2 original antes de crear la integración. El gateway público accede a esa IP pública; la IP privada `172.31.41.113` solo se usa entre las EC2.

## Cambio propuesto, después del inventario

Crear una integración HTTP_PROXY hacia `http://100.48.154.224:80` y conectar la ruta `$default` a su ID. No adjuntar Lambda Authorizer a `$default`. Conservar los targets y autorizadores actuales de `/api/v1/...`. La integración base debe permitir que Nginx resuelva tanto `/` como los archivos de Angular y las rutas de la SPA. Usar la consola de API Gateway o los comandos `create-integration` y `create-route` con los IDs comprobados en el inventario; evitar copiar identificadores de otro entorno.

Para demostrar el resultado, consultar la URL raíz del gateway y una ruta de Angular recargada directamente. Deben devolver HTML del frontend. Consultar una ruta API explícita sin token y con token válido: la primera debe ser rechazada por autorización y la segunda debe ir al microservicio. Si una ruta API devuelve HTML, falta una ruta o está mal conectada. La URL de `environment.prod.ts` ya señala `d4ehy77hhb` para las API principales.

## Entra ID y CORS

MSAL usa `${window.location.origin}/` como URI de retorno. Para abrir el frontend desde el gateway, registrar **exactamente** `https://d4ehy77hhb.execute-api.us-east-1.amazonaws.com/` como redirect URI de tipo SPA en el registro de aplicación de Entra ID, además de la URL HTTPS de la EC2 usada actualmente. Comprobar login y logout desde ambas URL.

El origen para CORS es solo esquema + host + puerto opcional, sin `/` final. Si la página se abre desde el gateway y las API también usan ese mismo origen, no hay solicitud de origen cruzado entre ambos. Conservar `https://100.48.154.224` y `http://localhost:4200` como entradas **separadas** si se siguen usando esas páginas para llamar al gateway. No pegar varios orígenes separados por comas en una sola entrada. Verificar `GET, POST, PUT, DELETE, OPTIONS` y encabezados `authorization, content-type`; volver a probar el preflight tras guardar los cambios.

Referencias: [selección de rutas de HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-routes.html), [integraciones HTTP](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-http.html), [CORS en HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html).
