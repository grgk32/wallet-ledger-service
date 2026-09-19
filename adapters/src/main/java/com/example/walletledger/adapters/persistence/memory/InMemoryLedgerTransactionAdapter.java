package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.LedgerWorkspace;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class InMemoryLedgerTransactionAdapter implements LedgerTransactionSpiPort {

    private final InMemoryLedgerStore store;
    private final AccountLockRegistry accountLockRegistry;

    public InMemoryLedgerTransactionAdapter(InMemoryLedgerStore store, AccountLockRegistry accountLockRegistry) {
        this.store = Objects.requireNonNull(store, "store");
        this.accountLockRegistry = Objects.requireNonNull(accountLockRegistry, "accountLockRegistry");
    }

    @Override
    public <R> R executeWithin(Set<AccountId> participants, Function<LedgerWorkspace, R> operation) {
        var acquiredLocks = accountLockRegistry.acquireAll(participants);
        try {
            var workspace = new InMemoryLedgerWorkspace(store, participants);
            var result = operation.apply(workspace);
            workspace.commit();
            return result;
        } finally {
            accountLockRegistry.releaseAll(acquiredLocks);
        }
    }
}
