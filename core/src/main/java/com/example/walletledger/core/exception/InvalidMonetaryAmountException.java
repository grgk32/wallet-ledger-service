package com.example.walletledger.core.exception;

import com.example.walletledger.core.model.RejectionReason;

public final class InvalidMonetaryAmountException extends LedgerDomainException {

    public InvalidMonetaryAmountException(String message) {
        super(RejectionReason.INVALID_MONETARY_AMOUNT, message);
    }
}
