package com.example.walletledger.adapters.metrics;

import com.example.walletledger.usecases.spi.AccountSpiPort;
import com.example.walletledger.usecases.spi.IdempotencyRecordSpiPort;
import com.example.walletledger.usecases.spi.LockContentionSpiPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class LedgerMetricsBinder implements MeterBinder {

    private static final String ACCOUNT_GAUGE_NAME = "wallet.ledger.accounts";
    private static final String IDEMPOTENCY_RECORD_GAUGE_NAME = "wallet.ledger.idempotency.records";
    private static final String CONTENDED_ACCOUNT_GAUGE_NAME = "wallet.ledger.locked.accounts";

    private final AccountSpiPort accounts;
    private final IdempotencyRecordSpiPort idempotencyRecords;
    private final LockContentionSpiPort lockContention;

    public LedgerMetricsBinder(AccountSpiPort accounts,
                               IdempotencyRecordSpiPort idempotencyRecords,
                               LockContentionSpiPort lockContention) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.idempotencyRecords = Objects.requireNonNull(idempotencyRecords, "idempotencyRecords");
        this.lockContention = Objects.requireNonNull(lockContention, "lockContention");
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        Gauge.builder(ACCOUNT_GAUGE_NAME, accounts, AccountSpiPort::countAccounts)
                .description("Accounts currently held by the ledger")
                .register(meterRegistry);
        Gauge.builder(IDEMPOTENCY_RECORD_GAUGE_NAME, idempotencyRecords, IdempotencyRecordSpiPort::countRecords)
                .description("Idempotency records retained for replay")
                .register(meterRegistry);
        Gauge.builder(CONTENDED_ACCOUNT_GAUGE_NAME, lockContention, LockContentionSpiPort::countTrackedAccounts)
                .description("Accounts for which a lock has been materialised")
                .register(meterRegistry);
    }
}
