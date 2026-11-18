package com.examen.catalogoapi.listener;

import com.examen.catalogoapi.model.ProcessedStockOrder;
import com.examen.catalogoapi.repository.ProcessedStockOrderRepository;
import com.examen.catalogoapi.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockConsumerTest {
    @Mock ProductoService productos;
    @Mock ProcessedStockOrderRepository processedOrders;
    @InjectMocks StockConsumer consumer;

    private static Map<String, Object> order() {
        return Map.of("orderId", "ORD-TEST-1",
                "items", List.of(Map.of("productoId", 10, "cantidad", 2)));
    }

    @Test
    void redeliveryDoesNotDiscountAgain() {
        when(processedOrders.existsById("ORD-TEST-1")).thenReturn(true);

        consumer.processStockReduction(order());

        verify(productos, never()).descontarStock(any(), anyInt());
        verify(processedOrders, never()).saveAndFlush(any(ProcessedStockOrder.class));
    }

    @Test
    void validOrderDiscountsAndRecordsMarker() {
        consumer.processStockReduction(order());

        verify(productos).descontarStock(10L, 2);
        verify(processedOrders).saveAndFlush(any(ProcessedStockOrder.class));
    }

    @Test
    void invalidQuantityDoesNotRecordMarker() {
        Map<String, Object> invalid = Map.of("orderId", "ORD-TEST-2",
                "items", List.of(Map.of("productoId", 10, "cantidad", 0)));

        assertThrows(IllegalArgumentException.class, () -> consumer.processStockReduction(invalid));

        verify(productos, never()).descontarStock(any(), anyInt());
        verify(processedOrders, never()).saveAndFlush(any(ProcessedStockOrder.class));
    }
}
