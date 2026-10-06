package com.pedidos360.notifications.controller;

import com.pedidos360.notifications.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final EmailService emailService;

    public NotificationController(EmailService emailService) {
        this.emailService = emailService;
    }

    // Endpoint directo para prueba desde Postman (Requisito Rúbrica Duoc UC)
    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> enviarNotificacionManual(@RequestBody Map<String, String> request) {
        String to = request.getOrDefault("to", "cliente@ejemplo.com");
        String subject = request.getOrDefault("subject", "Notificación de Prueba - Pedidos360");
        String body = request.getOrDefault("body", "Este es un correo de prueba emitido desde Postman.");

        emailService.enviarCorreo(to, subject, body);

        return ResponseEntity.ok(Map.of(
                "status", "ENVIADO",
                "mensaje", "Correo enviado exitosamente a " + to,
                "destinatario", to
        ));
    }

    @GetMapping("/ping")
    public String ping() {
        return "notifications-service está activo y escuchando RabbitMQ";
    }
}
