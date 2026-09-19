package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.AccountId;

import java.util.Set;
import java.util.function.Function;

public interface LedgerTransactionSpiPort {

    <R> R executeWithin(Set<AccountId> participants, Function<LedgerWorkspace, R> operation);
}
