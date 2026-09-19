package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;

import java.util.List;

public interface LedgerJournalSpiPort {

    List<LedgerEntry> findByAccount(AccountId accountId);
}
