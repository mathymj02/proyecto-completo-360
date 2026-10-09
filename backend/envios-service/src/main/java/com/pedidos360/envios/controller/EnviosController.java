package com.pedidos360.envios.controller;

import com.pedidos360.envios.listener.EnviosConsumer;
import com.pedidos360.envios.model.GuiaDespacho;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/envios")
@CrossOrigin(origins = "*")
public class EnviosController {

    private final EnviosConsumer enviosConsumer;

    public EnviosController(EnviosConsumer enviosConsumer) {
        this.enviosConsumer = enviosConsumer;
    }

    @GetMapping
    public ResponseEntity<Collection<GuiaDespacho>> listarGuias() {
        return ResponseEntity.ok(enviosConsumer.obtenerTodasLasGuias().values());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<GuiaDespacho> obtenerPorOrden(@PathVariable String orderId) {
        GuiaDespacho guia = enviosConsumer.obtenerGuia(orderId);
        if (guia != null) {
            return ResponseEntity.ok(guia);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/ping")
    public String ping() {
        return "envios-service está activo y procesando despachos con RabbitMQ";
    }
}
