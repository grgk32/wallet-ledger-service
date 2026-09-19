package com.example.walletledger.core.exception;

import com.example.walletledger.core.model.RejectionReason;

public final class CurrencyMismatchException extends LedgerDomainException {

    public CurrencyMismatchException(String expectedCurrencyCode, String actualCurrencyCode) {
        super(RejectionReason.CURRENCY_MISMATCH,
                "expected currency " + expectedCurrencyCode + " but received " + actualCurrencyCode);
    }
}
