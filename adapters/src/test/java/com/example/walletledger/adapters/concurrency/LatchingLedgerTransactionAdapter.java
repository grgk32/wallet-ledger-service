package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.LedgerWorkspace;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class LatchingLedgerTransactionAdapter implements LedgerTransactionSpiPort {

    private final LedgerTransactionSpiPort delegate;
    private final AtomicReference<Set<AccountId>> armedParticipants = new AtomicReference<>();
    private final CountDownLatch criticalSectionEntered = new CountDownLatch(1);
    private final CountDownLatch criticalSectionReleased = new CountDownLatch(1);

    LatchingLedgerTransactionAdapter(LedgerTransactionSpiPort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    void armFor(Set<AccountId> participants) {
        armedParticipants.set(Set.copyOf(participants));
    }

    boolean awaitCriticalSectionEntry(Duration budget) throws InterruptedException {
        return criticalSectionEntered.await(budget.toMillis(), TimeUnit.MILLISECONDS);
    }

    void releaseCriticalSection() {
        criticalSectionReleased.countDown();
    }

    @Override
    public <R> R executeWithin(Set<AccountId> participants, Function<LedgerWorkspace, R> operation) {
        return delegate.executeWithin(participants, workspace -> {
            latchWhenArmedFor(participants);
            return operation.apply(workspace);
        });
    }

    private void latchWhenArmedFor(Set<AccountId> participants) {
        var armed = armedParticipants.get();
        if (armed == null || !armed.equals(participants) || !armedParticipants.compareAndSet(armed, null)) {
            return;
        }
        criticalSectionEntered.countDown();
        awaitRelease();
    }

    private void awaitRelease() {
        try {
            criticalSectionReleased.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
