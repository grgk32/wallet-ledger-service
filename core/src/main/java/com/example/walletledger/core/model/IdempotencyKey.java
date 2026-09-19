package com.example.walletledger.core.model;

public record IdempotencyKey(String value) {

    private static final int MAXIMUM_LENGTH = 128;

    public IdempotencyKey {
        value = Identifiers.requireIdentifier(value, MAXIMUM_LENGTH, "idempotencyKey");
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
