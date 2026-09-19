package com.example.walletledger.usecases.service;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.service.LedgerPosting;
import com.example.walletledger.usecases.api.AccountSnapshot;
import com.example.walletledger.usecases.api.CreateAccountApiPort;
import com.example.walletledger.usecases.api.CreateAccountCommand;
import com.example.walletledger.usecases.spi.AccountIdGenerator;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.TransferIdGenerator;

import java.util.Objects;
import java.util.Set;

public final class CreateAccountService implements CreateAccountApiPort {

    private final LedgerTransactionSpiPort ledgerTransactions;
    private final AccountIdGenerator accountIdGenerator;
    private final TransferIdGenerator transferIdGenerator;
    private final ClockSpiPort clock;

    public CreateAccountService(LedgerTransactionSpiPort ledgerTransactions,
                                AccountIdGenerator accountIdGenerator,
                                TransferIdGenerator transferIdGenerator,
                                ClockSpiPort clock) {
        this.ledgerTransactions = Objects.requireNonNull(ledgerTransactions, "ledgerTransactions");
        this.accountIdGenerator = Objects.requireNonNull(accountIdGenerator, "accountIdGenerator");
        this.transferIdGenerator = Objects.requireNonNull(transferIdGenerator, "transferIdGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AccountSnapshot createAccount(CreateAccountCommand command) {
        var accountId = accountIdGenerator.nextAccountId();
        var openedAt = clock.now();
        var openingBalance = Money.of(command.initialBalance(), command.currency());
        var openedAccount = new Account(accountId, command.ownerReference(), openingBalance, openedAt);

        return ledgerTransactions.executeWithin(Set.of(accountId), workspace -> {
            workspace.save(openedAccount);
            if (openingBalance.isPositive()) {
                workspace.appendEntries(
                        LedgerPosting.fund(openedAccount, transferIdGenerator.nextTransferId(), openedAt));
            }
            return new AccountSnapshot(accountId, openedAccount.ownerReference(), openingBalance, openedAt);
        });
    }
}
