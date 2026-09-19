package com.example.walletledger.usecases.service;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.api.AccountSnapshot;
import com.example.walletledger.usecases.api.GetAccountBalanceApiPort;
import com.example.walletledger.usecases.exception.AccountNotFoundException;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;

import java.util.Objects;
import java.util.Set;

public final class GetAccountBalanceService implements GetAccountBalanceApiPort {

    private final LedgerTransactionSpiPort ledgerTransactions;
    private final ClockSpiPort clock;

    public GetAccountBalanceService(LedgerTransactionSpiPort ledgerTransactions, ClockSpiPort clock) {
        this.ledgerTransactions = Objects.requireNonNull(ledgerTransactions, "ledgerTransactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AccountSnapshot getBalance(AccountId accountId) {
        return ledgerTransactions.executeWithin(Set.of(accountId), workspace -> workspace.findAccount(accountId)
                .map(this::snapshotOf)
                .orElseThrow(() -> new AccountNotFoundException(accountId)));
    }

    private AccountSnapshot snapshotOf(Account account) {
        return new AccountSnapshot(account.accountId(), account.ownerReference(), account.balance(), clock.now());
    }
}
