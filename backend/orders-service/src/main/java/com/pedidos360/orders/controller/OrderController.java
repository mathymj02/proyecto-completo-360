package com.pedidos360.orders.controller;

import com.pedidos360.orders.config.RabbitMQConfig;
import com.pedidos360.orders.dto.OrderEvent;
import com.pedidos360.orders.service.PaymentVerifier;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = {"http://localhost:4200", "https://100.48.154.224"})
public class OrderController {

    private final RabbitTemplate rabbitTemplate;
    private final PaymentVerifier paymentVerifier;

    public OrderController(RabbitTemplate rabbitTemplate, PaymentVerifier paymentVerifier) {
        this.rabbitTemplate = rabbitTemplate;
        this.paymentVerifier = paymentVerifier;
    }

    @PostMapping
    public ResponseEntity<OrderEvent> crearOrden(@RequestBody OrderEvent request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        // Asignar ID unico y fecha si no vienen
        if (request.getItems() == null || request.getItems().isEmpty() || request.getClienteEmail() == null || request.getClienteEmail().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (!paymentVerifier.isAllowed(request.getPaymentId(), request.getTotal(), authorization)) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
        }
        request.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        if (request.getFecha() == null || request.getFecha().isEmpty()) {
            request.setFecha(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        // Publicar evento en RabbitMQ hacia el DirectExchange
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.ROUTING_KEY,
                    request,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return message;
                    }
            );
            // Segunda copia para auditoria/ruteo por patron. La cola de auditoria
            // usa orders.# y permite demostrar un TopicExchange real en RabbitMQ.
            operations.convertAndSend(
                    RabbitMQConfig.TOPIC_EXCHANGE,
                    RabbitMQConfig.TOPIC_ROUTING_KEY,
                    request,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        return message;
                    }
            );
            operations.waitForConfirmsOrDie(5000);
            return null;
        });

        System.out.println("[ORDERS-SERVICE] Orden publicada en RabbitMQ: " + request.getOrderId() + " para " + request.getClienteEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/ping")
    public String ping() {
        return "orders-service está activo y conectado a RabbitMQ";
    }
}
