package com.example.walletledger.usecases.api;

public interface TransferMoneyApiPort {

    TransferReceipt transfer(TransferCommand command);
}
