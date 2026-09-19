package com.example.walletledger.adapters.web;

import java.time.Duration;

public record RateLimitVerdict(boolean allowed, Duration retryAfter) {

    private static final RateLimitVerdict PERMITTED = new RateLimitVerdict(true, Duration.ZERO);

    public static RateLimitVerdict permit() {
        return PERMITTED;
    }

    public static RateLimitVerdict denyFor(Duration retryAfter) {
        return new RateLimitVerdict(false, retryAfter);
    }
}
