package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.AccountId;

public final class UnlockedAccountAccessException extends IllegalStateException {

    public UnlockedAccountAccessException(AccountId accountId) {
        super("account " + accountId.value() + " was accessed outside the participants of the current transaction");
    }
}
