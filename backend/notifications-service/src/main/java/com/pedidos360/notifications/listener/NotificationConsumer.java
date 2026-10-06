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
        String orderId = String.valueOf(order.getOrDefault("orderId", "N/A"));
        String clienteEmail = String.valueOf(order.getOrDefault("clienteEmail", "cliente@pedidos360.com"));
        Object total = order.getOrDefault("total", 0);

        System.out.println("[RABBITMQ-CONSUMER] Mensaje recibido de notifications_queue para orden: " + orderId);

        String asunto = "Confirmación de Compra - Pedidos360 #" + orderId;
        String cuerpo = "¡Hola!\n\nTu orden #" + orderId + " por un total de $" + total + " CLP ha sido confirmada con éxito.\n" +
                "Nuestro equipo de logística ya está preparando tu paquete.\n\n" +
                "Gracias por comprar en Pedidos360 Tech Store.";

        emailService.enviarCorreo(clienteEmail, asunto, cuerpo);
    }
}
