package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.TransferOutcome;
import com.example.walletledger.usecases.spi.ClaimResult;
import com.example.walletledger.usecases.spi.IdempotencyRecordSpiPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class CaffeineIdempotencyRecordAdapter implements IdempotencyRecordSpiPort {

    private final Cache<IdempotencyKey, IdempotencyRecord> records;

    public CaffeineIdempotencyRecordAdapter(long maximumRecords, Duration retention) {
        this.records = Caffeine.newBuilder()
                .maximumSize(maximumRecords)
                .expireAfterWrite(retention)
                .build();
    }

    @Override
    public ClaimResult claim(IdempotencyKey idempotencyKey, String requestFingerprint) {
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        var candidate = new IdempotencyRecord(requestFingerprint);
        var existingRecord = records.asMap().putIfAbsent(idempotencyKey, candidate);
        if (existingRecord == null) {
            return new ClaimResult.Claimed();
        }
        if (!existingRecord.matches(requestFingerprint)) {
            return new ClaimResult.Conflicting();
        }
        return existingRecord.settledOutcome()
                .<ClaimResult>map(ClaimResult.Replayed::new)
                .orElseGet(ClaimResult.InFlight::new);
    }

    @Override
    public Optional<TransferOutcome> awaitSettlement(IdempotencyKey idempotencyKey, Duration waitBudget) {
        var record = records.getIfPresent(idempotencyKey);
        return record == null ? Optional.empty() : record.awaitSettlement(waitBudget);
    }

    @Override
    public void settle(IdempotencyKey idempotencyKey, TransferOutcome outcome) {
        var record = records.getIfPresent(idempotencyKey);
        if (record != null) {
            record.settle(outcome);
        }
    }

    @Override
    public void release(IdempotencyKey idempotencyKey) {
        var record = records.asMap().remove(idempotencyKey);
        if (record != null) {
            record.abandon();
        }
    }

    @Override
    public long countRecords() {
        return records.estimatedSize();
    }

    private static final class IdempotencyRecord {

        private final String requestFingerprint;
        private final CompletableFuture<TransferOutcome> settlement = new CompletableFuture<>();

        private IdempotencyRecord(String requestFingerprint) {
            this.requestFingerprint = requestFingerprint;
        }

        private boolean matches(String otherFingerprint) {
            return requestFingerprint.equals(otherFingerprint);
        }

        private Optional<TransferOutcome> settledOutcome() {
            return Optional.ofNullable(settlement.getNow(null));
        }

        private void settle(TransferOutcome outcome) {
            settlement.complete(outcome);
        }

        private void abandon() {
            settlement.cancel(false);
        }

        private Optional<TransferOutcome> awaitSettlement(Duration waitBudget) {
            try {
                return Optional.of(settlement.get(waitBudget.toMillis(), TimeUnit.MILLISECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (TimeoutException | ExecutionException | CancellationException notSettled) {
                return Optional.empty();
            }
        }
    }
}
