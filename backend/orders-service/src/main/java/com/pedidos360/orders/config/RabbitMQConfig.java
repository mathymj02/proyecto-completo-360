package com.pedidos360.orders.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "pedidos_exchange";
    public static final String ROUTING_KEY = "orden.creada";

    public static final String STOCK_QUEUE = "stock_queue";
    public static final String NOTIFICATIONS_QUEUE = "notifications_queue";
    public static final String ENVIOS_QUEUE = "envios_queue";

    // 1. Declarar DirectExchange
    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    // 2. Declarar Queues durables
    @Bean
    public Queue stockQueue() {
        return new Queue(STOCK_QUEUE, true);
    }

    @Bean
    public Queue notificationsQueue() {
        return new Queue(NOTIFICATIONS_QUEUE, true);
    }

    @Bean
    public Queue enviosQueue() {
        return new Queue(ENVIOS_QUEUE, true);
    }

    // 3. Declarar Bindings con la Routing Key
    @Bean
    public Binding bindStockQueue(DirectExchange pedidosExchange, Queue stockQueue) {
        return BindingBuilder.bind(stockQueue).to(pedidosExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding bindNotificationsQueue(DirectExchange pedidosExchange, Queue notificationsQueue) {
        return BindingBuilder.bind(notificationsQueue).to(pedidosExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding bindEnviosQueue(DirectExchange pedidosExchange, Queue enviosQueue) {
        return BindingBuilder.bind(enviosQueue).to(pedidosExchange).with(ROUTING_KEY);
    }

    // 4. Convertidor JSON para serializar objetos automáticamente
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
