package com.examen.catalogoapi.listener;

import com.examen.catalogoapi.model.ProcessedStockOrder;
import com.examen.catalogoapi.repository.ProcessedStockOrderRepository;
import com.examen.catalogoapi.service.ProductoService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class StockConsumer {

    private final ProductoService productoService;
    private final ProcessedStockOrderRepository processedOrders;

    public StockConsumer(ProductoService productoService, ProcessedStockOrderRepository processedOrders) {
        this.productoService = productoService;
        this.processedOrders = processedOrders;
    }

    @RabbitListener(queues = "stock_queue")
    @Transactional
    public void processStockReduction(Map<String, Object> order) {
        System.out.println("==================================================");
        String orderId = order.get("orderId") == null ? "" : order.get("orderId").toString();
        if (orderId.isBlank() || orderId.length() > 64) {
            throw new IllegalArgumentException("Orden sin ID valido");
        }
        if (processedOrders.existsById(orderId)) {
            System.out.println("[CATALOGO-STOCK-CONSUMER] Orden ya procesada, se omite: " + orderId);
            return;
        }
        System.out.println("[CATALOGO-STOCK-CONSUMER] Orden recibida de RabbitMQ: " + orderId);

        Object itemsObj = order.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            throw new IllegalArgumentException("Orden sin items: " + orderId);
        }
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> item)
                    || item.get("productoId") == null
                    || item.get("cantidad") == null) {
                throw new IllegalArgumentException("Item invalido en orden: " + orderId);
            }
            Long productoId = Long.valueOf(item.get("productoId").toString());
            int cantidad = Integer.parseInt(item.get("cantidad").toString());
            if (cantidad <= 0) {
                throw new IllegalArgumentException("Cantidad invalida en orden: " + orderId);
            }
            productoService.descontarStock(productoId, cantidad);
        }
        // El marcador y los cambios de stock comparten la transacción SQL.
        // Si falla cualquier item, se revierte todo y el mensaje va a la DLQ.
        processedOrders.saveAndFlush(new ProcessedStockOrder(orderId));
        System.out.println("==================================================");
    }
}
