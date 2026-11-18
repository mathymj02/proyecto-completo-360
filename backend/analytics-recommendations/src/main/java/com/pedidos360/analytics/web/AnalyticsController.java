package com.pedidos360.analytics.web;

import com.pedidos360.analytics.service.MetricsService;
import com.pedidos360.analytics.service.RecommendationEngine;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final MetricsService metrics;
    private final RecommendationEngine recommendations;

    public AnalyticsController(MetricsService metrics, RecommendationEngine recommendations) {
        this.metrics = metrics;
        this.recommendations = recommendations;
    }

    @GetMapping("/metrics")
    public MetricsService.Snapshot metrics() {
        return metrics.snapshot();
    }

    @GetMapping("/recommendations/me")
    public Object recommendations(@AuthenticationPrincipal Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        return recommendations.recommend(oid != null ? oid : jwt.getSubject());
    }
}
