package com.example.walletledger.adapters.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled,
                                  @DefaultValue("100") long requestsPerPeriod,
                                  @DefaultValue("1m") Duration period,
                                  @DefaultValue("50000") long maximumTrackedClients,
                                  @DefaultValue("10m") Duration clientRetention) {
}
