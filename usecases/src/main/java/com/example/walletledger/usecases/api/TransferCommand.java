package com.example.walletledger.usecases.api;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.Money;

import java.util.Objects;

public record TransferCommand(IdempotencyKey idempotencyKey,
                              AccountId sourceAccountId,
                              AccountId targetAccountId,
                              Money amount) {

    public TransferCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(amount, "amount");
    }
}
