package com.example.walletledger.core.exception;

import com.example.walletledger.core.model.RejectionReason;

public final class InsufficientFundsException extends LedgerDomainException {

    public InsufficientFundsException(String accountIdentifier, String availableBalance, String requestedAmount) {
        super(RejectionReason.INSUFFICIENT_FUNDS,
                "account " + accountIdentifier + " holds " + availableBalance + " and cannot release " + requestedAmount);
    }
}
