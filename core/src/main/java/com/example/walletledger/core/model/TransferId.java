package com.example.walletledger.core.model;

public record TransferId(String value) {

    private static final int MAXIMUM_LENGTH = 64;

    public TransferId {
        value = Identifiers.requireIdentifier(value, MAXIMUM_LENGTH, "transferId");
    }

    public static TransferId of(String value) {
        return new TransferId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
