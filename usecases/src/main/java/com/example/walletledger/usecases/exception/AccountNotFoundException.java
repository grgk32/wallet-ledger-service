package com.example.walletledger.usecases.exception;

import com.example.walletledger.core.model.AccountId;

public final class AccountNotFoundException extends ApplicationException {

    private final AccountId accountId;

    public AccountNotFoundException(AccountId accountId) {
        super("account " + accountId.value() + " does not exist");
        this.accountId = accountId;
    }

    public AccountId accountId() {
        return accountId;
    }
}
