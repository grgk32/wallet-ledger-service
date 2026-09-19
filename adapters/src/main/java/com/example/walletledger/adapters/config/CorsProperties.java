package com.example.walletledger.adapters.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "cors")
public record CorsProperties(@DefaultValue("*") List<String> allowedOrigins,
                             @DefaultValue({"GET", "POST", "OPTIONS"}) List<String> allowedMethods,
                             @DefaultValue("*") List<String> allowedHeaders,
                             @DefaultValue({"Idempotency-Replayed", "Retry-After", "X-Correlation-Id"})
                             List<String> exposedHeaders,
                             @DefaultValue("1h") Duration maxAge) {
}
