package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.spi.AccountSpiPort;

import java.util.Objects;

public final class InMemoryAccountAdapter implements AccountSpiPort {

    private final InMemoryLedgerStore store;

    public InMemoryAccountAdapter(InMemoryLedgerStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public boolean existsById(AccountId accountId) {
        return store.containsAccount(accountId);
    }

    @Override
    public long countAccounts() {
        return store.accountCount();
    }
}
