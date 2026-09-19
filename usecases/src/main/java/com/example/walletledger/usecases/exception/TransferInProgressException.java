package com.example.walletledger.usecases.exception;

import com.example.walletledger.core.model.IdempotencyKey;

import java.time.Duration;

public final class TransferInProgressException extends ApplicationException {

    private final Duration waitBudget;

    public TransferInProgressException(IdempotencyKey idempotencyKey, Duration waitBudget) {
        super("the original request for idempotency key " + idempotencyKey.value()
                + " did not settle within " + waitBudget.toMillis() + " ms");
        this.waitBudget = waitBudget;
    }

    public Duration waitBudget() {
        return waitBudget;
    }
}
