package com.example.walletledger.core.exception;

import com.example.walletledger.core.model.RejectionReason;

public final class UnknownCurrencyException extends LedgerDomainException {

    public UnknownCurrencyException(String currencyCode) {
        super(RejectionReason.UNKNOWN_CURRENCY, "unknown ISO 4217 currency code: " + currencyCode);
    }
}
