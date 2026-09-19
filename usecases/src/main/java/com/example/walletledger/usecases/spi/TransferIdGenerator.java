package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.TransferId;

@FunctionalInterface
public interface TransferIdGenerator {

    TransferId nextTransferId();
}
