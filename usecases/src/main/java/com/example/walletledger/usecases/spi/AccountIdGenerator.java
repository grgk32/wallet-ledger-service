package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.AccountId;

@FunctionalInterface
public interface AccountIdGenerator {

    AccountId nextAccountId();
}
