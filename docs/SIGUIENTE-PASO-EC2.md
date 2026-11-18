# Siguiente paso: ampliar la EC2 existente

> **Actualización 18-11-2026:** el laboratorio rechazó `ec2:StartInstances` con `explicit deny` al intentar arrancar la EC2 como `t3.xlarge`. La instancia volvió a `t3.micro` y arrancó. Esta guía queda como procedimiento condicionado a que VocLabs/profesor habilite un tipo con suficiente RAM; no repetir el cambio de tipo sin comprobar la política del laboratorio.

Objetivo: disponer de RAM suficiente para probar tres ZooKeeper, tres brokers Kafka, dos nodos RabbitMQ, los servicios Java y Angular en la misma instancia. El disco ya es de 20 GiB; el `free -h` anterior mostró solo 913 MiB de RAM. Un tipo x86_64 con 16 GiB, como `t3.xlarge` si la cuenta del laboratorio lo permite, es un punto de partida para la demostración. La capacidad final se confirma midiendo consumo real. Revisa cuota y costo en tu cuenta antes de elegir.

## 1. Registrar el estado actual

En la consola EC2, anota **Instance ID**, **Instance type**, **Public IPv4 address** y si la dirección es **Elastic IP**. En la terminal de esa instancia, desde `/home/ec2-user/proyecto-completo`, ejecuta:

```bash
free -h
df -h /
docker compose ps
docker volume ls
```

Conserva la salida de esos cuatro comandos. No copies contraseñas ni `.env`. El repositorio de la EC2 contiene cambios locales; no ejecutes `git reset --hard`, `docker compose down -v` ni recrees RabbitMQ antes de respaldar sus datos.

## 2. Cambiar el tipo de instancia

En **EC2 → Instances**, selecciona la instancia. Usa **Instance state → Stop instance**; espera a `Stopped`. Luego **Actions → Instance settings → Change instance type** y elige una opción x86_64 de 16 GiB compatible (por ejemplo `t3.xlarge`, si aparece). Confirma y usa **Instance state → Start instance**. AWS exige detener una instancia EBS antes de cambiar el tipo. La IP pública automática puede cambiar; la IP privada y el volumen EBS permanecen asociados a la instancia.

Si no aparece ningún tipo compatible de 16 GiB o el laboratorio rechaza el cambio, registra el mensaje exacto. Crear otra instancia sería el plan alternativo, pero exige migrar el proyecto y sus datos.

## 3. Comprobar antes de desplegar software nuevo

Espera a que los dos **Status checks** estén correctos. Anota la nueva IPv4 pública y conecta a la misma instancia. Ejecuta:

```bash
free -h
df -h /
cd /home/ec2-user/proyecto-completo
docker compose ps
docker compose logs --since=5m rabbitmq orders-service catalogo-api
```

Primero comprueba que cargue el frontend existente y el catálogo. Si cambió la IP, las integraciones de API Gateway que todavía apuntan a `100.48.154.224`, el CORS, la URL de Angular, el retorno Webpay y la redirección de Entra necesitarán actualizarse. No desplegar Kafka ni modificar RabbitMQ antes de confirmar que la base anterior volvió a funcionar.

## 4. Continuación técnica

Cuando el punto 3 esté correcto: reconciliar cambios locales de EC2 con `main`; respaldar/migrar el volumen RabbitMQ; levantar y verificar los clústeres; crear topics y políticas DLQ; desplegar pagos y analítica; configurar DynamoDB, API Gateway y CORS; probar Webpay y WebSocket; por último, actualizar README y guardar evidencias de la rúbrica. El estado por requisito está en [AVANCE-EVALUACION.md](AVANCE-EVALUACION.md).

Referencias: [cambio de tipo EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/change-instance-type-of-ebs-backed-instance.html), [especificaciones EC2](https://docs.aws.amazon.com/ec2/latest/instancetypes/gp.html).
