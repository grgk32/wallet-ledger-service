package com.example.walletledger.core.model;

import com.example.walletledger.core.exception.InsufficientFundsException;
import com.example.walletledger.core.exception.InvalidIdentifierException;
import com.example.walletledger.core.exception.InvalidMonetaryAmountException;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public record Account(AccountId accountId, String ownerReference, Money balance, Instant openedAt) {

    private static final int MAXIMUM_OWNER_REFERENCE_LENGTH = 128;

    public Account {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(openedAt, "openedAt");
        ownerReference = requireOwnerReference(ownerReference);
    }

    public Currency currency() {
        return balance.currency();
    }

    public Account withdraw(Money requestedAmount) {
        requireTransferableAmount(requestedAmount);
        if (balance.isLessThan(requestedAmount)) {
            throw new InsufficientFundsException(
                    accountId.value(), balance.toString(), requestedAmount.toString());
        }
        return new Account(accountId, ownerReference, balance.subtract(requestedAmount), openedAt);
    }

    public Account deposit(Money requestedAmount) {
        requireTransferableAmount(requestedAmount);
        return new Account(accountId, ownerReference, balance.add(requestedAmount), openedAt);
    }

    private void requireTransferableAmount(Money requestedAmount) {
        if (requestedAmount == null) {
            throw new InvalidMonetaryAmountException("amount must not be null");
        }
        balance.requireSameCurrency(requestedAmount);
        if (!requestedAmount.isPositive()) {
            throw new InvalidMonetaryAmountException("amount must be strictly positive but was " + requestedAmount);
        }
    }

    private static String requireOwnerReference(String candidate) {
        if (candidate == null) {
            throw new InvalidIdentifierException("ownerReference must not be null");
        }
        var trimmed = candidate.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidIdentifierException("ownerReference must not be blank");
        }
        if (trimmed.length() > MAXIMUM_OWNER_REFERENCE_LENGTH) {
            throw new InvalidIdentifierException(
                    "ownerReference must not exceed " + MAXIMUM_OWNER_REFERENCE_LENGTH + " characters");
        }
        return trimmed;
    }
}
