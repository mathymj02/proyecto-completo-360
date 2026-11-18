package com.pedidos360.orders.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "pedidos_exchange";
    public static final String ROUTING_KEY = "orden.creada";

    public static final String STOCK_QUEUE = "stock_queue";
    public static final String NOTIFICATIONS_QUEUE = "notifications_queue";
    public static final String ENVIOS_QUEUE = "envios_queue";
    public static final String DEAD_LETTER_EXCHANGE = "pedidos_dlx";
    public static final String TOPIC_EXCHANGE = "pedidos_topic_exchange";
    public static final String TOPIC_ROUTING_KEY = "orders.created";

    // 1. Declarar DirectExchange
    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public TopicExchange pedidosTopicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE);
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

    @Bean
    public Queue stockDeadLetterQueue() {
        return new Queue("stock_dlq", true);
    }

    @Bean
    public Queue notificationsDeadLetterQueue() {
        return new Queue("notifications_dlq", true);
    }

    @Bean
    public Queue enviosDeadLetterQueue() {
        return new Queue("envios_dlq", true);
    }

    @Bean
    public Queue ordersAuditQueue() {
        return new Queue("orders_audit_queue", true);
    }

    // 3. Declarar Bindings con la Routing Key
    @Bean
    public Binding bindStockQueue(@Qualifier("pedidosExchange") DirectExchange pedidosExchange,
                                  @Qualifier("stockQueue") Queue stockQueue) {
        return BindingBuilder.bind(stockQueue).to(pedidosExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding bindNotificationsQueue(@Qualifier("pedidosExchange") DirectExchange pedidosExchange,
                                          @Qualifier("notificationsQueue") Queue notificationsQueue) {
        return BindingBuilder.bind(notificationsQueue).to(pedidosExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding bindEnviosQueue(@Qualifier("pedidosExchange") DirectExchange pedidosExchange,
                                   @Qualifier("enviosQueue") Queue enviosQueue) {
        return BindingBuilder.bind(enviosQueue).to(pedidosExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding bindStockDeadLetterQueue(@Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
                                            @Qualifier("stockDeadLetterQueue") Queue stockDeadLetterQueue) {
        return BindingBuilder.bind(stockDeadLetterQueue).to(deadLetterExchange).with("dead.stock");
    }

    @Bean
    public Binding bindNotificationsDeadLetterQueue(@Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
                                                    @Qualifier("notificationsDeadLetterQueue") Queue notificationsDeadLetterQueue) {
        return BindingBuilder.bind(notificationsDeadLetterQueue).to(deadLetterExchange).with("dead.notifications");
    }

    @Bean
    public Binding bindEnviosDeadLetterQueue(@Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
                                             @Qualifier("enviosDeadLetterQueue") Queue enviosDeadLetterQueue) {
        return BindingBuilder.bind(enviosDeadLetterQueue).to(deadLetterExchange).with("dead.envios");
    }

    @Bean
    public Binding bindOrdersAuditQueue(@Qualifier("pedidosTopicExchange") TopicExchange pedidosTopicExchange,
                                        @Qualifier("ordersAuditQueue") Queue ordersAuditQueue) {
        return BindingBuilder.bind(ordersAuditQueue).to(pedidosTopicExchange).with("orders.#");
    }

    // 4. Convertidor JSON para serializar objetos automáticamente
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
