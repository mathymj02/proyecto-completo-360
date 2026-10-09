package com.pedidos360.orders.controller;

import com.pedidos360.orders.config.RabbitMQConfig;
import com.pedidos360.orders.dto.OrderEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final RabbitTemplate rabbitTemplate;

    public OrderController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    public ResponseEntity<OrderEvent> crearOrden(@RequestBody OrderEvent request) {
        // Asignar ID unico y fecha si no vienen
        if (request.getOrderId() == null || request.getOrderId().isEmpty()) {
            request.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (request.getFecha() == null || request.getFecha().isEmpty()) {
            request.setFecha(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        // Publicar evento en RabbitMQ hacia el DirectExchange
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                request
        );

        System.out.println("[ORDERS-SERVICE] Orden publicada en RabbitMQ: " + request.getOrderId() + " para " + request.getClienteEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/ping")
    public String ping() {
        return "orders-service está activo y conectado a RabbitMQ";
    }
}
