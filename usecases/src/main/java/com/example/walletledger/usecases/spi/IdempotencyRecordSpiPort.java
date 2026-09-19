package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.TransferOutcome;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyRecordSpiPort {

    ClaimResult claim(IdempotencyKey idempotencyKey, String requestFingerprint);

    Optional<TransferOutcome> awaitSettlement(IdempotencyKey idempotencyKey, Duration waitBudget);

    void settle(IdempotencyKey idempotencyKey, TransferOutcome outcome);

    void release(IdempotencyKey idempotencyKey);

    long countRecords();
}
