package com.example.walletledger.core.model;

import java.time.Instant;
import java.util.Objects;

public sealed interface TransferOutcome {

    record Applied(TransferId transferId,
                   AccountId sourceAccountId,
                   AccountId targetAccountId,
                   Money amount,
                   Instant postedAt) implements TransferOutcome {

        public Applied {
            Objects.requireNonNull(transferId, "transferId");
            Objects.requireNonNull(sourceAccountId, "sourceAccountId");
            Objects.requireNonNull(targetAccountId, "targetAccountId");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(postedAt, "postedAt");
        }
    }

    record Rejected(RejectionReason reason, String detail) implements TransferOutcome {

        public Rejected {
            Objects.requireNonNull(reason, "reason");
            detail = detail == null ? reason.name() : detail;
        }
    }
}
