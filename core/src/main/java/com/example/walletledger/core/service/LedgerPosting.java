package com.example.walletledger.core.service;

import com.example.walletledger.core.exception.SameAccountTransferException;
import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.EntryDirection;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class LedgerPosting {

    private LedgerPosting() {
    }

    public static TransferPosting post(Account source,
                                       Account target,
                                       Money amount,
                                       TransferId transferId,
                                       Instant postedAt) {
        if (source.accountId().equals(target.accountId())) {
            throw new SameAccountTransferException(source.accountId().value());
        }
        var debitedSource = source.withdraw(amount);
        var creditedTarget = target.deposit(amount);
        return new TransferPosting(debitedSource, creditedTarget,
                balancedEntryPair(source.accountId(), target.accountId(), amount, transferId, postedAt));
    }

    public static List<LedgerEntry> fund(Account fundedAccount, TransferId transferId, Instant postedAt) {
        return balancedEntryPair(AccountId.externalFundingSource(), fundedAccount.accountId(),
                fundedAccount.balance(), transferId, postedAt);
    }

    private static List<LedgerEntry> balancedEntryPair(AccountId debitedAccountId,
                                                       AccountId creditedAccountId,
                                                       Money amount,
                                                       TransferId transferId,
                                                       Instant postedAt) {
        return List.of(
                new LedgerEntry(entryIdFor(transferId, EntryDirection.DEBIT), transferId, debitedAccountId,
                        EntryDirection.DEBIT, amount, postedAt),
                new LedgerEntry(entryIdFor(transferId, EntryDirection.CREDIT), transferId, creditedAccountId,
                        EntryDirection.CREDIT, amount, postedAt));
    }

    private static String entryIdFor(TransferId transferId, EntryDirection direction) {
        return transferId.value() + ":" + direction.name().toLowerCase(Locale.ROOT);
    }
}
