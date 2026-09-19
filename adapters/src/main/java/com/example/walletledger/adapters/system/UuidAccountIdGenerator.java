package com.example.walletledger.adapters.system;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.spi.AccountIdGenerator;

import java.util.UUID;

public final class UuidAccountIdGenerator implements AccountIdGenerator {

    @Override
    public AccountId nextAccountId() {
        return AccountId.of(UUID.randomUUID().toString());
    }
}
