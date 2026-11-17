package com.examen.catalogoapi.listener;

import com.examen.catalogoapi.service.ProductoService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class StockConsumer {

    private final ProductoService productoService;

    public StockConsumer(ProductoService productoService) {
        this.productoService = productoService;
    }

    @RabbitListener(queues = "stock_queue")
    @Transactional
    public void processStockReduction(Map<String, Object> order) {
        System.out.println("==================================================");
        System.out.println("[CATALOGO-STOCK-CONSUMER] Orden recibida de RabbitMQ: " + order.get("orderId"));

        Object itemsObj = order.get("items");
        if (itemsObj instanceof List) {
            List<?> items = (List<?>) itemsObj;
            for (Object itemObj : items) {
                if (itemObj instanceof Map) {
                    Map<?, ?> item = (Map<?, ?>) itemObj;
                    Object prodIdObj = item.get("productoId");
                    Object cantObj = item.get("cantidad");

                    if (prodIdObj != null && cantObj != null) {
                        Long productoId = Long.valueOf(prodIdObj.toString());
                        int cantidad = Integer.parseInt(cantObj.toString());

                        productoService.descontarStock(productoId, cantidad);
                    }
                }
            }
        }
        System.out.println("==================================================");
    }
}
