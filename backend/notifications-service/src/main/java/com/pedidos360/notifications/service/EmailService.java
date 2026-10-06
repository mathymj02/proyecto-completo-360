package com.pedidos360.notifications.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void enviarCorreo(String to, String subject, String body) {
        System.out.println("==================================================");
        System.out.println("[NOTIFICATIONS-SERVICE] ENVIANDO CORREO POR GMAIL");
        System.out.println("Destinatario: " + to);
        System.out.println("Asunto:       " + subject);
        System.out.println("Cuerpo:       " + body);
        System.out.println("==================================================");

        if (mailSender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                message.setFrom("notificaciones.pedidos360@gmail.com");
                mailSender.send(message);
                System.out.println("[NOTIFICATIONS-SERVICE] Correo despachado exitosamente vía SMTP Gmail.");
            } catch (Exception e) {
                System.err.println("[NOTIFICATIONS-SERVICE] Aviso: Error conectando a SMTP Gmail (" + e.getMessage() + "). Notificación registrada en consola.");
            }
        }
    }
}
