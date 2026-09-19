package com.example.walletledger.adapters.health;

import com.example.walletledger.usecases.spi.AccountSpiPort;
import com.example.walletledger.usecases.spi.IdempotencyRecordSpiPort;
import com.example.walletledger.usecases.spi.LockContentionSpiPort;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component("ledger")
public class LedgerHealthIndicator implements HealthIndicator {

    private static final String ACCOUNT_DETAIL = "accounts";
    private static final String IDEMPOTENCY_RECORD_DETAIL = "idempotencyRecords";
    private static final String LOCKED_ACCOUNT_DETAIL = "lockedAccounts";

    private final AccountSpiPort accounts;
    private final IdempotencyRecordSpiPort idempotencyRecords;
    private final LockContentionSpiPort lockContention;

    public LedgerHealthIndicator(AccountSpiPort accounts,
                                 IdempotencyRecordSpiPort idempotencyRecords,
                                 LockContentionSpiPort lockContention) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.idempotencyRecords = Objects.requireNonNull(idempotencyRecords, "idempotencyRecords");
        this.lockContention = Objects.requireNonNull(lockContention, "lockContention");
    }

    @Override
    public Health health() {
        try {
            return Health.up()
                    .withDetail(ACCOUNT_DETAIL, accounts.countAccounts())
                    .withDetail(IDEMPOTENCY_RECORD_DETAIL, idempotencyRecords.countRecords())
                    .withDetail(LOCKED_ACCOUNT_DETAIL, lockContention.countTrackedAccounts())
                    .build();
        } catch (RuntimeException storeUnreadable) {
            return Health.down(storeUnreadable).build();
        }
    }
}
