package com.example.walletledger.usecases.api;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;

import java.time.Instant;
import java.util.Objects;

public record TransferReceipt(TransferId transferId,
                              AccountId sourceAccountId,
                              AccountId targetAccountId,
                              Money amount,
                              Instant postedAt,
                              boolean replayed) {

    public TransferReceipt {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(postedAt, "postedAt");
    }
}
