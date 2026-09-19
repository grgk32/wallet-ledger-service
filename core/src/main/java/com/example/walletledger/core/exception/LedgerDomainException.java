package com.example.walletledger.core.exception;

import com.example.walletledger.core.model.RejectionReason;

public abstract class LedgerDomainException extends RuntimeException {

    private final RejectionReason rejectionReason;

    protected LedgerDomainException(RejectionReason rejectionReason, String message) {
        super(message);
        this.rejectionReason = rejectionReason;
    }

    public RejectionReason rejectionReason() {
        return rejectionReason;
    }
}
