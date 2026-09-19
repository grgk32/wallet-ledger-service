package com.example.walletledger.usecases.api;

import com.example.walletledger.core.model.AccountId;

public interface GetAccountBalanceApiPort {

    AccountSnapshot getBalance(AccountId accountId);
}
