package com.pedidos360.analytics.service;

import com.pedidos360.analytics.model.InteractionEvent;
import com.pedidos360.analytics.model.PaymentAttemptEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;
import java.util.HashMap;

@Service
public class DynamoEventStore {
    private final ObjectProvider<DynamoDbClient> client;
    private final boolean enabled;
    private final String table;

    public DynamoEventStore(ObjectProvider<DynamoDbClient> client,
                            @Value("${analytics.dynamodb.enabled:false}") boolean enabled,
                            @Value("${analytics.dynamodb.table:Pedidos360Events}") String table) {
        this.client = client;
        this.enabled = enabled;
        this.table = table;
    }

    public void save(InteractionEvent event) {
        if (!enabled) return;
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("eventId", text(event.eventId()));
        item.put("eventType", text(event.type()));
        item.put("userId", text(event.userId()));
        item.put("productId", text(event.productId().toString()));
        item.put("occurredAt", text(event.occurredAt().toString()));
        if (event.category() != null) item.put("category", text(event.category()));
        client.getObject().putItem(PutItemRequest.builder().tableName(table).item(item).build());
    }

    public void save(PaymentAttemptEvent event) {
        if (!enabled) return;
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("eventId", text(event.eventId()));
        item.put("eventType", text("PAYMENT_ATTEMPT"));
        item.put("userId", text(event.userId()));
        item.put("orderId", text(event.orderId()));
        item.put("status", text(event.status()));
        item.put("occurredAt", text(event.occurredAt().toString()));
        client.getObject().putItem(PutItemRequest.builder().tableName(table).item(item).build());
    }

    private static AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }

    @Configuration
    @ConditionalOnProperty(name = "analytics.dynamodb.enabled", havingValue = "true")
    static class ClientConfig {
        @Bean(destroyMethod = "close")
        DynamoDbClient dynamoDbClient() {
            return DynamoDbClient.create();
        }
    }
}
