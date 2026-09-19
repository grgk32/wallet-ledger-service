package com.example.walletledger.adapters.metrics;

import com.example.walletledger.usecases.api.TransferCommand;
import com.example.walletledger.usecases.api.TransferMoneyApiPort;
import com.example.walletledger.usecases.api.TransferReceipt;
import com.example.walletledger.usecases.exception.IdempotencyKeyReusedException;
import com.example.walletledger.usecases.exception.TransferInProgressException;
import com.example.walletledger.usecases.exception.TransferRejectedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class TransferMetricsDecorator implements TransferMoneyApiPort {

    private static final String TRANSFER_COUNTER_NAME = "wallet.ledger.transfers";
    private static final String TRANSFER_TIMER_NAME = "wallet.ledger.transfer.duration";
    private static final String OUTCOME_TAG = "outcome";
    private static final String REASON_TAG = "reason";
    private static final String NO_REASON = "none";
    private static final String OUTCOME_APPLIED = "applied";
    private static final String OUTCOME_REPLAYED = "replayed";
    private static final String OUTCOME_REJECTED = "rejected";
    private static final String OUTCOME_CONFLICTED = "conflicted";
    private static final String OUTCOME_IN_PROGRESS = "in_progress";

    private final TransferMoneyApiPort delegate;
    private final MeterRegistry meterRegistry;
    private final Timer transferTimer;

    public TransferMetricsDecorator(TransferMoneyApiPort delegate, MeterRegistry meterRegistry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.transferTimer = Timer.builder(TRANSFER_TIMER_NAME)
                .description("Wall clock duration of a transfer request, including idempotent replays")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    @Override
    public TransferReceipt transfer(TransferCommand command) {
        var startedAtNanos = System.nanoTime();
        try {
            var receipt = delegate.transfer(command);
            countOutcome(receipt.replayed() ? OUTCOME_REPLAYED : OUTCOME_APPLIED, NO_REASON);
            return receipt;
        } catch (TransferRejectedException rejection) {
            countOutcome(OUTCOME_REJECTED, rejection.reason().name().toLowerCase(Locale.ROOT));
            throw rejection;
        } catch (IdempotencyKeyReusedException keyReuse) {
            countOutcome(OUTCOME_CONFLICTED, NO_REASON);
            throw keyReuse;
        } catch (TransferInProgressException stillRunning) {
            countOutcome(OUTCOME_IN_PROGRESS, NO_REASON);
            throw stillRunning;
        } finally {
            transferTimer.record(System.nanoTime() - startedAtNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void countOutcome(String outcome, String reason) {
        Counter.builder(TRANSFER_COUNTER_NAME)
                .description("Transfer requests classified by their terminal outcome")
                .tag(OUTCOME_TAG, outcome)
                .tag(REASON_TAG, reason)
                .register(meterRegistry)
                .increment();
    }
}
