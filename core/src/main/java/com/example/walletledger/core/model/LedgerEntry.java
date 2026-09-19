package com.example.walletledger.core.model;

import java.time.Instant;
import java.util.Objects;

public record LedgerEntry(String entryId,
                          TransferId transferId,
                          AccountId accountId,
                          EntryDirection direction,
                          Money amount,
                          Instant postedAt) {

    public LedgerEntry {
        entryId = Identifiers.requireIdentifier(entryId, 160, "entryId");
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(postedAt, "postedAt");
    }

    public boolean isDebit() {
        return direction == EntryDirection.DEBIT;
    }

    public boolean isCredit() {
        return direction == EntryDirection.CREDIT;
    }
}
