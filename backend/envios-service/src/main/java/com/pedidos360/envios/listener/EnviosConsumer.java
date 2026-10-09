package com.pedidos360.envios.listener;

import com.pedidos360.envios.model.GuiaDespacho;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EnviosConsumer {

    // Repositorio en memoria de guias de despacho
    private final Map<String, GuiaDespacho> guiasPorOrden = new ConcurrentHashMap<>();

    @RabbitListener(queues = "envios_queue")
    public void processOrderForShipping(Map<String, Object> order) {
        String orderId = String.valueOf(order.getOrDefault("orderId", "ORD-UNKNOWN"));
        String clienteEmail = String.valueOf(order.getOrDefault("clienteEmail", "cliente@pedidos360.com"));

        String tracking = "TRK-" + (int)(10000 + Math.random() * 90000) + "-CL";
        String entregaEstimada = LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        GuiaDespacho guia = new GuiaDespacho(
                tracking,
                orderId,
                clienteEmail,
                "EN_PREPARACION",
                "Despacho a Domicilio (Región Metropolitana)",
                entregaEstimada
        );

        guiasPorOrden.put(orderId, guia);

        System.out.println("==================================================");
        System.out.println("[ENVIOS-SERVICE] GUÍA DE DESPACHO GENERADA");
        System.out.println("Orden:    " + orderId);
        System.out.println("Tracking: " + tracking);
        System.out.println("Cliente:  " + clienteEmail);
        System.out.println("Estado:   " + guia.getEstado());
        System.out.println("Entrega:  " + entregaEstimada);
        System.out.println("==================================================");
    }

    public GuiaDespacho obtenerGuia(String orderId) {
        return guiasPorOrden.get(orderId);
    }

    public Map<String, GuiaDespacho> obtenerTodasLasGuias() {
        return guiasPorOrden;
    }
}
