package com.example.walletledger.adapters.web;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";

    private static final String UNASSIGNED = "unassigned";
    private static final int MAXIMUM_LENGTH = 64;

    private CorrelationId() {
    }

    public static String current() {
        return Optional.ofNullable(MDC.get(MDC_KEY)).orElse(UNASSIGNED);
    }

    static String resolve(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }
        var trimmed = candidate.strip();
        return trimmed.length() > MAXIMUM_LENGTH ? trimmed.substring(0, MAXIMUM_LENGTH) : trimmed;
    }
}
