package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryLedgerStore {

    private final ConcurrentMap<AccountId, Account> accountsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<AccountId, AccountJournal> journalsByAccount = new ConcurrentHashMap<>();

    public Optional<Account> findAccount(AccountId accountId) {
        return Optional.ofNullable(accountsById.get(accountId));
    }

    public void putAccount(Account account) {
        accountsById.put(account.accountId(), account);
    }

    public boolean containsAccount(AccountId accountId) {
        return accountsById.containsKey(accountId);
    }

    public long accountCount() {
        return accountsById.size();
    }

    public List<Account> allAccounts() {
        return List.copyOf(accountsById.values());
    }

    public void appendEntry(LedgerEntry entry) {
        journalsByAccount.computeIfAbsent(entry.accountId(), accountId -> new AccountJournal()).append(entry);
    }

    public List<LedgerEntry> entriesOf(AccountId accountId) {
        var journal = journalsByAccount.get(accountId);
        return journal == null ? List.of() : journal.snapshot();
    }

    public List<LedgerEntry> allEntries() {
        return journalsByAccount.values().stream().flatMap(journal -> journal.snapshot().stream()).toList();
    }

    private static final class AccountJournal {

        private final List<LedgerEntry> entries = new ArrayList<>();

        private synchronized void append(LedgerEntry entry) {
            entries.add(entry);
        }

        private synchronized List<LedgerEntry> snapshot() {
            return List.copyOf(entries);
        }
    }
}
