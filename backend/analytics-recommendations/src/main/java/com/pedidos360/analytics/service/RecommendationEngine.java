package com.pedidos360.analytics.service;

import com.pedidos360.analytics.model.InteractionEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Service
public class RecommendationEngine {
    public record Recommendation(Long productId, String name, String category, long views) {}
    private final Map<String, Deque<Long>> recentByUser = new ConcurrentHashMap<>();
    private final Map<Long, Recommendation> products = new ConcurrentHashMap<>();
    private final Map<Long, LongAdder> views = new ConcurrentHashMap<>();

    public void record(InteractionEvent event) {
        if (event.productId() == null || event.userId() == null) return;
        Deque<Long> recent = recentByUser.computeIfAbsent(event.userId(), ignored -> new ArrayDeque<>());
        synchronized (recent) {
            recent.remove(event.productId());
            recent.addFirst(event.productId());
            while (recent.size() > 20) recent.removeLast();
        }
        if ("VIEW_PRODUCT".equals(event.type())) {
            views.computeIfAbsent(event.productId(), ignored -> new LongAdder()).increment();
            products.put(event.productId(), new Recommendation(event.productId(),
                    event.productName(), event.category(), views.get(event.productId()).sum()));
        }
    }

    public List<Recommendation> recommend(String userId) {
        Deque<Long> recent = recentByUser.get(userId);
        if (recent == null) return List.of();
        Set<Long> seen;
        String preferredCategory = null;
        synchronized (recent) {
            seen = new HashSet<>(recent);
            for (Long id : recent) {
                Recommendation product = products.get(id);
                if (product != null && product.category() != null) {
                    preferredCategory = product.category();
                    break;
                }
            }
        }
        String category = preferredCategory;
        return products.values().stream()
                .filter(product -> !seen.contains(product.productId()))
                .filter(product -> category == null || category.equalsIgnoreCase(product.category()))
                .map(product -> new Recommendation(product.productId(), product.name(),
                        product.category(), views.get(product.productId()).sum()))
                .sorted(Comparator.comparingLong(Recommendation::views).reversed())
                .limit(5)
                .toList();
    }

    public List<Recommendation> topProducts() {
        return products.values().stream()
                .map(product -> new Recommendation(product.productId(), product.name(),
                        product.category(), views.get(product.productId()).sum()))
                .sorted(Comparator.comparingLong(Recommendation::views).reversed())
                .limit(5)
                .toList();
    }
}
