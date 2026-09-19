package com.example.walletledger.usecases.api;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;

import java.util.List;

public interface GetAccountEntriesApiPort {

    List<LedgerEntry> findEntries(AccountId accountId);
}
