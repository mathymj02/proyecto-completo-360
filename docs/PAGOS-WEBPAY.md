# Pagos de prueba con Webpay Plus

**Estado:** el código de `ms-pago` y la pantalla de retorno están en `main`;
la integración completa en AWS aún debe probarse antes de activar el botón
de pago en Angular. Usar exclusivamente el ambiente de integración público de
Transbank y tarjetas de prueba descritas en su documentación.

## Flujo

1. Desde el frontend abierto en la URL de API Gateway, Angular envía el total
   y un **alias de prueba** a `POST /api/v1/payments`. El alias nunca es un
   número de tarjeta.
2. Nginx de la EC2 original envía la solicitud por la red privada a `ms-pago`
   en la EC2 nueva. `ms-pago` valida el JWT de Microsoft Entra ID, registra
   el intento en su propia MariaDB y solicita a Webpay un token y una URL.
3. El navegador envía el token a la página de Webpay. La tarjeta se introduce
   solo allí. El backend no recibe PAN ni CVV.
4. Webpay regresa a `/payment-return/:paymentId` del mismo origen donde
   empezó la compra. Angular envía `token_ws` a `ms-pago`, que confirma la
   transacción con Webpay y compara ID, monto, estado y código de respuesta.
5. Solo si el pago queda `AUTHORIZED`, Angular publica la orden en
   `orders-service`. Este envía el evento a RabbitMQ; catálogo, notificaciones
   y envíos lo consumen de forma asíncrona. El carrito se vacía después.
6. Cada transición de pago publica un evento en `intentos-pago` de Kafka. La
   analítica incrementa intentos y alertas, y envía la instantánea por
   WebSocket al panel.

```text
Angular -> API Gateway -> Nginx -> ms-pago -> Webpay integración
                                   | MariaDB propia
                                   | Kafka: intentos-pago -> analítica -> WebSocket
Webpay -> Angular /payment-return -> ms-pago /commit -> orders-service -> RabbitMQ
```

## Antifraude de demostración

La regla bloquea el cuarto intento con el mismo alias dentro de diez
segundos. Guarda un hash SHA-256 con una sal local, no el alias en claro. Esta
regla demuestra un control de frecuencia en el laboratorio; **no identifica
la tarjeta real**. Webpay Plus captura la tarjeta fuera de nuestra aplicación
y este flujo no recibe un identificador estable de ella.

## Despliegue y límites

`docker-compose.platform-ec2.yml` define `mariadb-pagos` con volumen propio y
`ms-pago`. `scripts/init-payment-env.py` prepara el `.env` local: genera dos
contraseñas de base y la sal, y coloca las credenciales **públicas** del
ambiente de integración. No imprime secretos. El nombre del comercio y las
claves productivas no se versionan. El puerto 8087 se publica solo en la IP
privada de la EC2 nueva; su grupo de seguridad debe aceptar TCP 8087 desde el
grupo de seguridad de la EC2 original. El frontend usa la ruta del Gateway
para llamar a `/api/v1/payments`, que pasa por Nginx y llega a `ms-pago`.

La URL de retorno se configura como el origen HTTPS del Gateway. Abrir la
compra desde ese mismo origen: la orden pendiente vive en `sessionStorage`,
que el navegador separa por origen. Si se abre la tienda desde la IP HTTPS
de EC2 y Webpay retorna al Gateway, la orden pendiente no estará disponible.
No activar `paymentsEnabled` ni afirmar que Webpay funciona hasta comprobar
inicio, aprobación, rechazo, retorno y publicación de orden en el ambiente de
integración. Una transacción aprobada y una orden son operaciones distintas;
si falla la publicación de orden tras aprobarse el pago, la interfaz debe
mostrar el error y evitar repetir el cobro.

Fuentes: [inicio con credenciales de integración](https://transbankdevelopers.com/documentacion/como_empezar),
[flujo Webpay Plus](https://transbankdevelopers.com/documentacion/webpay-plus).
