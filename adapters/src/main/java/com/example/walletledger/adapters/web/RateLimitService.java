package com.example.walletledger.adapters.web;

import com.example.walletledger.adapters.config.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final RateLimitProperties rateLimitProperties;
    private final Cache<String, Bucket> bucketsByClient;

    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
        this.bucketsByClient = Caffeine.newBuilder()
                .maximumSize(rateLimitProperties.maximumTrackedClients())
                .expireAfterAccess(rateLimitProperties.clientRetention())
                .build();
    }

    public RateLimitVerdict tryConsume(String clientIdentifier) {
        if (!rateLimitProperties.enabled()) {
            return RateLimitVerdict.permit();
        }
        var probe = bucketsByClient.get(clientIdentifier, client -> newBucket()).tryConsumeAndReturnRemaining(1);
        return probe.isConsumed()
                ? RateLimitVerdict.permit()
                : RateLimitVerdict.denyFor(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rateLimitProperties.requestsPerPeriod())
                        .refillIntervally(rateLimitProperties.requestsPerPeriod(), rateLimitProperties.period())
                        .build())
                .build();
    }
}
