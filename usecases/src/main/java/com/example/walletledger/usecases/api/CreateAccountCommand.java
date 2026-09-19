package com.example.walletledger.usecases.api;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record CreateAccountCommand(String ownerReference, Currency currency, BigDecimal initialBalance) {

    public CreateAccountCommand {
        Objects.requireNonNull(ownerReference, "ownerReference");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(initialBalance, "initialBalance");
    }
}
