package com.example.walletledger.core.service;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.LedgerEntry;

import java.util.List;
import java.util.Objects;

public record TransferPosting(Account debitedSource, Account creditedTarget, List<LedgerEntry> entries) {

    public TransferPosting {
        Objects.requireNonNull(debitedSource, "debitedSource");
        Objects.requireNonNull(creditedTarget, "creditedTarget");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}
