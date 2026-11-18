package com.pedidos360.notifications.listener;

import com.pedidos360.notifications.service.EmailService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationConsumer {

    private final EmailService emailService;

    public NotificationConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = "notifications_queue")
    public void receiveOrderNotification(Map<String, Object> order) {
        String orderId = order.get("orderId") == null ? "" : order.get("orderId").toString();
        String clienteEmail = order.get("clienteEmail") == null ? "" : order.get("clienteEmail").toString();
        if (orderId.isBlank() || clienteEmail.isBlank()) {
            throw new IllegalArgumentException("Orden sin ID o correo para notificación");
        }
        Object total = order.getOrDefault("total", 0);

        System.out.println("[RABBITMQ-CONSUMER] Mensaje recibido de notifications_queue para orden: " + orderId);

        String asunto = "Confirmación de Compra - Pedidos360 #" + orderId;
        String cuerpo = "¡Hola!\n\nTu orden #" + orderId + " por un total de $" + total + " CLP ha sido confirmada con éxito.\n" +
                "Nuestro equipo de logística ya está preparando tu paquete.\n\n" +
                "Gracias por comprar en Pedidos360 Tech Store.";

        emailService.enviarCorreo(clienteEmail, asunto, cuerpo);
    }
}
