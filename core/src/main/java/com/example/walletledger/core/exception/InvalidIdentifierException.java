package com.example.walletledger.core.exception;

import com.example.walletledger.core.model.RejectionReason;

public final class InvalidIdentifierException extends LedgerDomainException {

    public InvalidIdentifierException(String message) {
        super(RejectionReason.INVALID_IDENTIFIER, message);
    }
}
