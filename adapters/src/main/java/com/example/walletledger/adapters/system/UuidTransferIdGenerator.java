package com.example.walletledger.adapters.system;

import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.usecases.spi.TransferIdGenerator;

import java.util.UUID;

public final class UuidTransferIdGenerator implements TransferIdGenerator {

    @Override
    public TransferId nextTransferId() {
        return TransferId.of(UUID.randomUUID().toString());
    }
}
