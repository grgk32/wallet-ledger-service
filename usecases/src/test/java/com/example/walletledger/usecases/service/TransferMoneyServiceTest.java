package com.example.walletledger.usecases.service;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.RejectionReason;
import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.core.model.TransferOutcome;
import com.example.walletledger.usecases.api.TransferCommand;
import com.example.walletledger.usecases.exception.IdempotencyKeyReusedException;
import com.example.walletledger.usecases.exception.TransferInProgressException;
import com.example.walletledger.usecases.exception.TransferRejectedException;
import com.example.walletledger.usecases.spi.ClaimResult;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.IdempotencyRecordSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.LedgerWorkspace;
import com.example.walletledger.usecases.spi.TransferIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransferMoneyServiceTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final IdempotencyKey IDEMPOTENCY_KEY = IdempotencyKey.of("key-1");
    private static final AccountId SOURCE_ID = AccountId.of("source");
    private static final AccountId TARGET_ID = AccountId.of("target");
    private static final TransferId TRANSFER_ID = TransferId.of("transfer-1");
    private static final Instant NOW = Instant.parse("2026-06-06T09:00:00Z");
    private static final Duration WAIT_BUDGET = Duration.ofMillis(200);

    @Mock
    private LedgerTransactionSpiPort ledgerTransactions;

    @Mock
    private IdempotencyRecordSpiPort idempotencyRecords;

    @Mock
    private TransferIdGenerator transferIdGenerator;

    @Mock
    private ClockSpiPort clock;

    @Mock
    private LedgerWorkspace workspace;

    private TransferMoneyService transferMoneyService;

    @BeforeEach
    void createService() {
        transferMoneyService = new TransferMoneyService(ledgerTransactions, idempotencyRecords, transferIdGenerator,
                clock, WAIT_BUDGET);
    }

    @Test
    void shouldApplyTransferWhenClaimIsGranted() {
        givenClaim(new ClaimResult.Claimed());
        givenLedgerHolds(accountWith(SOURCE_ID, "100.00"), accountWith(TARGET_ID, "0.00"));
        given(transferIdGenerator.nextTransferId()).willReturn(TRANSFER_ID);
        given(clock.now()).willReturn(NOW);

        var receipt = transferMoneyService.transfer(transferCommand("25.00"));

        assertThat(receipt).extracting(transferReceipt -> transferReceipt.transferId(),
                        transferReceipt -> transferReceipt.amount(), transferReceipt -> transferReceipt.replayed())
                .containsExactly(TRANSFER_ID, euros("25.00"), false);
    }

    @Test
    void shouldSettleTheClaimWhenTransferIsApplied() {
        givenClaim(new ClaimResult.Claimed());
        givenLedgerHolds(accountWith(SOURCE_ID, "100.00"), accountWith(TARGET_ID, "0.00"));
        given(transferIdGenerator.nextTransferId()).willReturn(TRANSFER_ID);
        given(clock.now()).willReturn(NOW);
        var outcomeCaptor = ArgumentCaptor.forClass(TransferOutcome.class);

        transferMoneyService.transfer(transferCommand("25.00"));

        then(idempotencyRecords).should().settle(eq(IDEMPOTENCY_KEY), outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue()).isInstanceOf(TransferOutcome.Applied.class);
    }

    @Test
    void shouldReturnStoredOutcomeWhenClaimIsReplayed() {
        givenClaim(new ClaimResult.Replayed(new TransferOutcome.Applied(TRANSFER_ID, SOURCE_ID, TARGET_ID,
                euros("25.00"), NOW)));

        var receipt = transferMoneyService.transfer(transferCommand("25.00"));

        assertThat(receipt.replayed()).isTrue();
    }

    @Test
    void shouldNotTouchTheLedgerWhenClaimIsReplayed() {
        givenClaim(new ClaimResult.Replayed(new TransferOutcome.Applied(TRANSFER_ID, SOURCE_ID, TARGET_ID,
                euros("25.00"), NOW)));

        transferMoneyService.transfer(transferCommand("25.00"));

        verifyNoInteractions(ledgerTransactions);
    }

    @Test
    void shouldReplayTheOriginalOutcomeWhenDuplicateArrivesInFlight() {
        givenClaim(new ClaimResult.InFlight());
        given(idempotencyRecords.awaitSettlement(IDEMPOTENCY_KEY, WAIT_BUDGET))
                .willReturn(Optional.of(new TransferOutcome.Applied(TRANSFER_ID, SOURCE_ID, TARGET_ID,
                        euros("25.00"), NOW)));

        var receipt = transferMoneyService.transfer(transferCommand("25.00"));

        assertThat(receipt).extracting(transferReceipt -> transferReceipt.transferId(),
                        transferReceipt -> transferReceipt.replayed())
                .containsExactly(TRANSFER_ID, true);
    }

    @Test
    void shouldRaiseInProgressWhenOriginalRequestDoesNotSettleWithinBudget() {
        givenClaim(new ClaimResult.InFlight());
        given(idempotencyRecords.awaitSettlement(IDEMPOTENCY_KEY, WAIT_BUDGET)).willReturn(Optional.empty());

        assertThatThrownBy(() -> transferMoneyService.transfer(transferCommand("25.00")))
                .isInstanceOf(TransferInProgressException.class)
                .extracting(thrown -> ((TransferInProgressException) thrown).waitBudget())
                .isEqualTo(WAIT_BUDGET);
    }

    @Test
    void shouldRaiseKeyReuseWhenFingerprintDiffers() {
        givenClaim(new ClaimResult.Conflicting());

        assertThatThrownBy(() -> transferMoneyService.transfer(transferCommand("25.00")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void shouldRejectTransferWhenSourceAndTargetAreTheSameAccount() {
        givenClaim(new ClaimResult.Claimed());

        assertThatThrownBy(() -> transferMoneyService.transfer(
                new TransferCommand(IDEMPOTENCY_KEY, SOURCE_ID, SOURCE_ID, euros("25.00"))))
                .isInstanceOf(TransferRejectedException.class)
                .extracting(thrown -> ((TransferRejectedException) thrown).reason())
                .isEqualTo(RejectionReason.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void shouldNotAcquireLedgerLocksWhenSourceAndTargetAreTheSameAccount() {
        givenClaim(new ClaimResult.Claimed());

        assertThatThrownBy(() -> transferMoneyService.transfer(
                new TransferCommand(IDEMPOTENCY_KEY, SOURCE_ID, SOURCE_ID, euros("25.00"))))
                .isInstanceOf(TransferRejectedException.class);
        then(ledgerTransactions).should(never()).executeWithin(any(), any());
    }

    @Test
    void shouldRejectTransferWhenSourceAccountIsAbsent() {
        givenClaim(new ClaimResult.Claimed());
        givenLedgerHolds(null, accountWith(TARGET_ID, "0.00"));
        given(transferIdGenerator.nextTransferId()).willReturn(TRANSFER_ID);

        assertThatThrownBy(() -> transferMoneyService.transfer(transferCommand("25.00")))
                .isInstanceOf(TransferRejectedException.class)
                .extracting(thrown -> ((TransferRejectedException) thrown).reason())
                .isEqualTo(RejectionReason.SOURCE_ACCOUNT_NOT_FOUND);
    }

    @Test
    void shouldRejectTransferWhenTargetAccountIsAbsent() {
        givenClaim(new ClaimResult.Claimed());
        givenLedgerHolds(accountWith(SOURCE_ID, "100.00"), null);
        given(transferIdGenerator.nextTransferId()).willReturn(TRANSFER_ID);

        assertThatThrownBy(() -> transferMoneyService.transfer(transferCommand("25.00")))
                .isInstanceOf(TransferRejectedException.class)
                .extracting(thrown -> ((TransferRejectedException) thrown).reason())
                .isEqualTo(RejectionReason.TARGET_ACCOUNT_NOT_FOUND);
    }

    @Test
    void shouldRejectTransferWhenBalanceIsInsufficient() {
        givenClaim(new ClaimResult.Claimed());
        givenLedgerHolds(accountWith(SOURCE_ID, "10.00"), accountWith(TARGET_ID, "0.00"));
        given(transferIdGenerator.nextTransferId()).willReturn(TRANSFER_ID);
        given(clock.now()).willReturn(NOW);

        assertThatThrownBy(() -> transferMoneyService.transfer(transferCommand("25.00")))
                .isInstanceOf(TransferRejectedException.class)
                .extracting(thrown -> ((TransferRejectedException) thrown).reason())
                .isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void shouldSettleTheRejectionWhenTransferIsRejected() {
        givenClaim(new ClaimResult.Claimed());
        givenLedgerHolds(accountWith(SOURCE_ID, "10.00"), accountWith(TARGET_ID, "0.00"));
        given(transferIdGenerator.nextTransferId()).willReturn(TRANSFER_ID);
        given(clock.now()).willReturn(NOW);
        var outcomeCaptor = ArgumentCaptor.forClass(TransferOutcome.class);

        assertThatThrownBy(() -> transferMoneyService.transfer(transferCommand("25.00")))
                .isInstanceOf(TransferRejectedException.class);
        then(idempotencyRecords).should().settle(eq(IDEMPOTENCY_KEY), outcomeCaptor.capture());
        assertThat(outcomeCaptor.getValue()).isInstanceOf(TransferOutcome.Rejected.class);
    }

    @Test
    void shouldReleaseTheClaimWhenLedgerFailsUnexpectedly() {
        givenClaim(new ClaimResult.Claimed());
        given(transferIdGenerator.nextTransferId()).willReturn(TRANSFER_ID);
        willThrow(new IllegalStateException("storage unavailable"))
                .given(ledgerTransactions).executeWithin(any(), any());

        assertThatThrownBy(() -> transferMoneyService.transfer(transferCommand("25.00")))
                .isInstanceOf(IllegalStateException.class);
        then(idempotencyRecords).should().release(IDEMPOTENCY_KEY);
    }

    @Test
    void shouldNotSettleTheClaimWhenLedgerFailsUnexpectedly() {
        givenClaim(new ClaimResult.Claimed());
        given(transferIdGenerator.nextTransferId()).willReturn(TRANSFER_ID);
        willThrow(new IllegalStateException("storage unavailable"))
                .given(ledgerTransactions).executeWithin(any(), any());

        assertThatThrownBy(() -> transferMoneyService.transfer(transferCommand("25.00")))
                .isInstanceOf(IllegalStateException.class);
        then(idempotencyRecords).should(never()).settle(any(), any());
    }

    private void givenClaim(ClaimResult claimResult) {
        given(idempotencyRecords.claim(eq(IDEMPOTENCY_KEY), anyString())).willReturn(claimResult);
    }

    private void givenLedgerHolds(Account source, Account target) {
        given(ledgerTransactions.executeWithin(any(), any())).willAnswer(invocation -> {
            Function<LedgerWorkspace, Object> operation = invocation.getArgument(1);
            return operation.apply(workspace);
        });
        given(workspace.findAccount(SOURCE_ID)).willReturn(Optional.ofNullable(source));
        if (source != null) {
            given(workspace.findAccount(TARGET_ID)).willReturn(Optional.ofNullable(target));
        }
    }

    private static TransferCommand transferCommand(String amount) {
        return new TransferCommand(IDEMPOTENCY_KEY, SOURCE_ID, TARGET_ID, euros(amount));
    }

    private static Account accountWith(AccountId accountId, String balance) {
        return new Account(accountId, "owner-" + accountId.value(), euros(balance), NOW);
    }

    private static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }
}
