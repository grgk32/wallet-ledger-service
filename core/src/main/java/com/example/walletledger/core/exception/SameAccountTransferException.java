package com.example.walletledger.core.exception;

import com.example.walletledger.core.model.RejectionReason;

public final class SameAccountTransferException extends LedgerDomainException {

    public SameAccountTransferException(String accountIdentifier) {
        super(RejectionReason.SAME_ACCOUNT_TRANSFER,
                "source and target account are identical: " + accountIdentifier);
    }
}
