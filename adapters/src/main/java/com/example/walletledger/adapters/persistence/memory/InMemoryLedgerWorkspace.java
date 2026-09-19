package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.usecases.spi.LedgerWorkspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class InMemoryLedgerWorkspace implements LedgerWorkspace {

    private final InMemoryLedgerStore store;
    private final Set<AccountId> participants;
    private final Map<AccountId, Account> stagedAccounts = new LinkedHashMap<>();
    private final List<LedgerEntry> stagedEntries = new ArrayList<>();

    InMemoryLedgerWorkspace(InMemoryLedgerStore store, Set<AccountId> participants) {
        this.store = store;
        this.participants = participants;
    }

    @Override
    public Optional<Account> findAccount(AccountId accountId) {
        requireParticipant(accountId);
        var stagedAccount = stagedAccounts.get(accountId);
        return stagedAccount == null ? store.findAccount(accountId) : Optional.of(stagedAccount);
    }

    @Override
    public void save(Account account) {
        requireParticipant(account.accountId());
        stagedAccounts.put(account.accountId(), account);
    }

    @Override
    public void appendEntries(List<LedgerEntry> entries) {
        stagedEntries.addAll(entries);
    }

    void commit() {
        stagedAccounts.values().forEach(store::putAccount);
        stagedEntries.forEach(store::appendEntry);
    }

    private void requireParticipant(AccountId accountId) {
        if (!participants.contains(accountId)) {
            throw new UnlockedAccountAccessException(accountId);
        }
    }
}
