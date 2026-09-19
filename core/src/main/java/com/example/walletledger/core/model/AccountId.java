package com.example.walletledger.core.model;

public record AccountId(String value) implements Comparable<AccountId> {

    private static final int MAXIMUM_LENGTH = 64;
    private static final AccountId EXTERNAL_FUNDING_SOURCE = new AccountId("external-funding-source");

    public AccountId {
        value = Identifiers.requireIdentifier(value, MAXIMUM_LENGTH, "accountId");
    }

    public static AccountId of(String value) {
        return new AccountId(value);
    }

    public static AccountId externalFundingSource() {
        return EXTERNAL_FUNDING_SOURCE;
    }

    public boolean isExternalFundingSource() {
        return EXTERNAL_FUNDING_SOURCE.value.equals(value);
    }

    @Override
    public int compareTo(AccountId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
