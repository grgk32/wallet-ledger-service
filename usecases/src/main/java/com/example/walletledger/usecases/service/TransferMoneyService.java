package com.example.walletledger.usecases.service;

import com.example.walletledger.core.exception.LedgerDomainException;
import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.RejectionReason;
import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.core.model.TransferOutcome;
import com.example.walletledger.core.service.LedgerPosting;
import com.example.walletledger.usecases.api.TransferCommand;
import com.example.walletledger.usecases.api.TransferMoneyApiPort;
import com.example.walletledger.usecases.api.TransferReceipt;
import com.example.walletledger.usecases.exception.IdempotencyKeyReusedException;
import com.example.walletledger.usecases.exception.TransferInProgressException;
import com.example.walletledger.usecases.exception.TransferRejectedException;
import com.example.walletledger.usecases.spi.ClaimResult;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.IdempotencyRecordSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.LedgerWorkspace;
import com.example.walletledger.usecases.spi.TransferIdGenerator;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

public final class TransferMoneyService implements TransferMoneyApiPort {

    private final LedgerTransactionSpiPort ledgerTransactions;
    private final IdempotencyRecordSpiPort idempotencyRecords;
    private final TransferIdGenerator transferIdGenerator;
    private final ClockSpiPort clock;
    private final Duration inFlightWaitBudget;

    public TransferMoneyService(LedgerTransactionSpiPort ledgerTransactions,
                                IdempotencyRecordSpiPort idempotencyRecords,
                                TransferIdGenerator transferIdGenerator,
                                ClockSpiPort clock,
                                Duration inFlightWaitBudget) {
        this.ledgerTransactions = Objects.requireNonNull(ledgerTransactions, "ledgerTransactions");
        this.idempotencyRecords = Objects.requireNonNull(idempotencyRecords, "idempotencyRecords");
        this.transferIdGenerator = Objects.requireNonNull(transferIdGenerator, "transferIdGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.inFlightWaitBudget = Objects.requireNonNull(inFlightWaitBudget, "inFlightWaitBudget");
    }

    @Override
    public TransferReceipt transfer(TransferCommand command) {
        var claim = idempotencyRecords.claim(command.idempotencyKey(), TransferFingerprint.of(command));
        if (claim instanceof ClaimResult.Conflicting) {
            throw new IdempotencyKeyReusedException(command.idempotencyKey());
        }
        if (claim instanceof ClaimResult.Replayed settled) {
            return receiptFrom(settled.outcome(), true);
        }
        if (claim instanceof ClaimResult.InFlight) {
            return awaitOriginalRequest(command);
        }
        return executeClaimedRequest(command);
    }

    private TransferReceipt awaitOriginalRequest(TransferCommand command) {
        return idempotencyRecords.awaitSettlement(command.idempotencyKey(), inFlightWaitBudget)
                .map(outcome -> receiptFrom(outcome, true))
                .orElseThrow(() -> new TransferInProgressException(command.idempotencyKey(), inFlightWaitBudget));
    }

    private TransferReceipt executeClaimedRequest(TransferCommand command) {
        TransferOutcome outcome;
        try {
            outcome = applyTransfer(command);
        } catch (RuntimeException unexpectedFailure) {
            idempotencyRecords.release(command.idempotencyKey());
            throw unexpectedFailure;
        }
        idempotencyRecords.settle(command.idempotencyKey(), outcome);
        return receiptFrom(outcome, false);
    }

    private TransferOutcome applyTransfer(TransferCommand command) {
        if (command.sourceAccountId().equals(command.targetAccountId())) {
            return new TransferOutcome.Rejected(RejectionReason.SAME_ACCOUNT_TRANSFER,
                    "source and target account are identical: " + command.sourceAccountId().value());
        }
        var transferId = transferIdGenerator.nextTransferId();
        var participants = Set.of(command.sourceAccountId(), command.targetAccountId());
        return ledgerTransactions.executeWithin(participants, workspace -> post(workspace, command, transferId));
    }

    private TransferOutcome post(LedgerWorkspace workspace, TransferCommand command, TransferId transferId) {
        var source = workspace.findAccount(command.sourceAccountId());
        if (source.isEmpty()) {
            return new TransferOutcome.Rejected(RejectionReason.SOURCE_ACCOUNT_NOT_FOUND,
                    "source account " + command.sourceAccountId().value() + " does not exist");
        }
        var target = workspace.findAccount(command.targetAccountId());
        if (target.isEmpty()) {
            return new TransferOutcome.Rejected(RejectionReason.TARGET_ACCOUNT_NOT_FOUND,
                    "target account " + command.targetAccountId().value() + " does not exist");
        }
        return postBetween(workspace, source.get(), target.get(), command, transferId);
    }

    private TransferOutcome postBetween(LedgerWorkspace workspace,
                                        Account source,
                                        Account target,
                                        TransferCommand command,
                                        TransferId transferId) {
        var postedAt = clock.now();
        try {
            var posting = LedgerPosting.post(source, target, command.amount(), transferId, postedAt);
            workspace.save(posting.debitedSource());
            workspace.save(posting.creditedTarget());
            workspace.appendEntries(posting.entries());
            return new TransferOutcome.Applied(transferId, source.accountId(), target.accountId(),
                    command.amount(), postedAt);
        } catch (LedgerDomainException rejection) {
            return new TransferOutcome.Rejected(rejection.rejectionReason(), rejection.getMessage());
        }
    }

    private TransferReceipt receiptFrom(TransferOutcome outcome, boolean replayed) {
        if (outcome instanceof TransferOutcome.Applied applied) {
            return new TransferReceipt(applied.transferId(), applied.sourceAccountId(), applied.targetAccountId(),
                    applied.amount(), applied.postedAt(), replayed);
        }
        var rejected = (TransferOutcome.Rejected) outcome;
        throw new TransferRejectedException(rejected.reason(), rejected.detail());
    }
}
