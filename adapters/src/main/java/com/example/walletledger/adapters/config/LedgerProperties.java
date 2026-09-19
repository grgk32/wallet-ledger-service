package com.example.walletledger.adapters.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(@DefaultValue("2s") Duration inFlightWaitBudget,
                               @DefaultValue("100000") long idempotencyMaximumRecords,
                               @DefaultValue("30m") Duration idempotencyRetention) {
}
