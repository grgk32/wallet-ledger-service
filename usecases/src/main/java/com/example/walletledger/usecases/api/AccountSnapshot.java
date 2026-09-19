package com.example.walletledger.usecases.api;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.Money;

import java.time.Instant;
import java.util.Objects;

public record AccountSnapshot(AccountId accountId, String ownerReference, Money balance, Instant observedAt) {

    public AccountSnapshot {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(ownerReference, "ownerReference");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
