package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;

import java.util.List;
import java.util.Optional;

public interface LedgerWorkspace {

    Optional<Account> findAccount(AccountId accountId);

    void save(Account account);

    void appendEntries(List<LedgerEntry> entries);
}
