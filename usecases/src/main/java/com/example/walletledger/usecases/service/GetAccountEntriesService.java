package com.example.walletledger.usecases.service;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.usecases.api.GetAccountEntriesApiPort;
import com.example.walletledger.usecases.exception.AccountNotFoundException;
import com.example.walletledger.usecases.spi.AccountSpiPort;
import com.example.walletledger.usecases.spi.LedgerJournalSpiPort;

import java.util.List;
import java.util.Objects;

public final class GetAccountEntriesService implements GetAccountEntriesApiPort {

    private final AccountSpiPort accounts;
    private final LedgerJournalSpiPort ledgerJournal;

    public GetAccountEntriesService(AccountSpiPort accounts, LedgerJournalSpiPort ledgerJournal) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.ledgerJournal = Objects.requireNonNull(ledgerJournal, "ledgerJournal");
    }

    @Override
    public List<LedgerEntry> findEntries(AccountId accountId) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return ledgerJournal.findByAccount(accountId);
    }
}
