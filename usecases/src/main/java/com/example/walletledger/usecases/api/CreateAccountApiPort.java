package com.example.walletledger.usecases.api;

public interface CreateAccountApiPort {

    AccountSnapshot createAccount(CreateAccountCommand command);
}
