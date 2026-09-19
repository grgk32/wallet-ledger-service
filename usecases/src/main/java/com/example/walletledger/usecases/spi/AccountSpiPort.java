package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.AccountId;

public interface AccountSpiPort {

    boolean existsById(AccountId accountId);

    long countAccounts();
}
