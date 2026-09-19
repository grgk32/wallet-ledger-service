package com.example.walletledger.usecases.service;

import com.example.walletledger.usecases.api.TransferCommand;

final class TransferFingerprint {

    private static final String FIELD_SEPARATOR = "|";

    private TransferFingerprint() {
    }

    static String of(TransferCommand command) {
        return String.join(FIELD_SEPARATOR,
                command.sourceAccountId().value(),
                command.targetAccountId().value(),
                command.amount().amount().toPlainString(),
                command.amount().currency().getCurrencyCode());
    }
}
