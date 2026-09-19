package com.example.walletledger.usecases.exception;

import com.example.walletledger.core.model.RejectionReason;

public final class TransferRejectedException extends ApplicationException {

    private final RejectionReason reason;

    public TransferRejectedException(RejectionReason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public RejectionReason reason() {
        return reason;
    }
}
