package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.usecases.spi.LedgerJournalSpiPort;

import java.util.List;
import java.util.Objects;

public final class InMemoryLedgerJournalAdapter implements LedgerJournalSpiPort {

    private final InMemoryLedgerStore store;

    public InMemoryLedgerJournalAdapter(InMemoryLedgerStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public List<LedgerEntry> findByAccount(AccountId accountId) {
        return store.entriesOf(accountId);
    }
}
