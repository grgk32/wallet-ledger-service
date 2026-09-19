package com.example.walletledger.core.model;

import com.example.walletledger.core.exception.InvalidIdentifierException;

final class Identifiers {

    private Identifiers() {
    }

    static String requireIdentifier(String candidate, int maximumLength, String attributeName) {
        if (candidate == null) {
            throw new InvalidIdentifierException(attributeName + " must not be null");
        }
        var trimmed = candidate.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidIdentifierException(attributeName + " must not be blank");
        }
        if (trimmed.length() > maximumLength) {
            throw new InvalidIdentifierException(
                    attributeName + " must not exceed " + maximumLength + " characters");
        }
        return trimmed;
    }
}
