package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.TransferOutcome;

import java.util.Objects;

public sealed interface ClaimResult {

    record Claimed() implements ClaimResult {
    }

    record Replayed(TransferOutcome outcome) implements ClaimResult {

        public Replayed {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    record InFlight() implements ClaimResult {
    }

    record Conflicting() implements ClaimResult {
    }
}
