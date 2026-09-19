package com.example.walletledger.usecases.exception;

import com.example.walletledger.core.model.IdempotencyKey;

public final class IdempotencyKeyReusedException extends ApplicationException {

    public IdempotencyKeyReusedException(IdempotencyKey idempotencyKey) {
        super("idempotency key " + idempotencyKey.value() + " was already used for a different request payload");
    }
}
