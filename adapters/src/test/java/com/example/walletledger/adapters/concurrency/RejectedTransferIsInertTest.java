package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.RejectionReason;
import com.example.walletledger.usecases.exception.TransferRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RejectedTransferIsInertTest {

    private static final AccountId UNKNOWN_ACCOUNT_ID = AccountId.of("unknown-account");
    private static final String SOURCE_OPENING_BALANCE = "100.00";
    private static final String TARGET_OPENING_BALANCE = "25.00";

    private LedgerTestFixture ledger;
    private AccountId sourceAccountId;
    private AccountId targetAccountId;
    private AccountId foreignCurrencyAccountId;

    @BeforeEach
    void openAccounts() {
        ledger = new LedgerTestFixture();
        sourceAccountId = ledger.openAccount(SOURCE_OPENING_BALANCE);
        targetAccountId = ledger.openAccount(TARGET_OPENING_BALANCE);
        foreignCurrencyAccountId = ledger.openAccount("10", LedgerTestFixture.DOLLAR);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RejectionScenario.class)
    void shouldRaiseTheExpectedReasonWhenTransferIsRejected(RejectionScenario scenario) {
        // when
        var rejection = assertThatThrownBy(() -> postRejectedTransfer(scenario));

        // then
        rejection.isInstanceOf(TransferRejectedException.class)
                .extracting(thrown -> ((TransferRejectedException) thrown).reason())
                .isEqualTo(scenario.expectedReason());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RejectionScenario.class)
    void shouldLeaveBalancesUnchangedWhenTransferIsRejected(RejectionScenario scenario) {
        // given
        var balancesBefore = ledger.allAccounts();

        // when
        assertThatThrownBy(() -> postRejectedTransfer(scenario)).isInstanceOf(TransferRejectedException.class);

        // then
        assertThat(ledger.allAccounts()).containsExactlyInAnyOrderElementsOf(balancesBefore);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RejectionScenario.class)
    void shouldLeaveJournalUnchangedWhenTransferIsRejected(RejectionScenario scenario) {
        // given
        var journalBefore = ledger.allEntries();

        // when
        assertThatThrownBy(() -> postRejectedTransfer(scenario)).isInstanceOf(TransferRejectedException.class);

        // then
        assertThat(ledger.allEntries()).containsExactlyInAnyOrderElementsOf(journalBefore);
    }

    @Test
    void shouldReplayTheRejectionWhenTheSameKeyIsRetried() {
        // given
        assertThatThrownBy(() -> ledger.transfer(sourceAccountId, targetAccountId, "500.00", "inert-replay"))
                .isInstanceOf(TransferRejectedException.class);

        // when
        var retry = assertThatThrownBy(
                () -> ledger.transfer(sourceAccountId, targetAccountId, "500.00", "inert-replay"));

        // then
        retry.isInstanceOf(TransferRejectedException.class)
                .extracting(thrown -> ((TransferRejectedException) thrown).reason())
                .isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void shouldPostNoEntriesWhenTransferIsRejectedRepeatedly() {
        // given
        var journalBefore = ledger.allEntries();

        // when
        for (var attempt = 0; attempt < 10; attempt++) {
            var idempotencyKey = "inert-repeat-" + attempt;
            assertThatThrownBy(() -> ledger.transfer(sourceAccountId, targetAccountId, "500.00", idempotencyKey))
                    .isInstanceOf(TransferRejectedException.class);
        }

        // then
        assertThat(ledger.allEntries()).containsExactlyInAnyOrderElementsOf(journalBefore);
    }

    private void postRejectedTransfer(RejectionScenario scenario) {
        var idempotencyKey = "inert-" + scenario.name().toLowerCase(Locale.ROOT);
        switch (scenario) {
            case OVERDRAWN_SOURCE -> ledger.transfer(sourceAccountId, targetAccountId, "500.00", idempotencyKey);
            case SAME_ACCOUNT -> ledger.transfer(sourceAccountId, sourceAccountId, "1.00", idempotencyKey);
            case UNKNOWN_SOURCE -> ledger.transfer(UNKNOWN_ACCOUNT_ID, targetAccountId, "1.00", idempotencyKey);
            case UNKNOWN_TARGET -> ledger.transfer(sourceAccountId, UNKNOWN_ACCOUNT_ID, "1.00", idempotencyKey);
            case CURRENCY_MISMATCH ->
                    ledger.transfer(sourceAccountId, foreignCurrencyAccountId, "1.00", idempotencyKey);
        }
    }

    private enum RejectionScenario {

        OVERDRAWN_SOURCE(RejectionReason.INSUFFICIENT_FUNDS),
        SAME_ACCOUNT(RejectionReason.SAME_ACCOUNT_TRANSFER),
        UNKNOWN_SOURCE(RejectionReason.SOURCE_ACCOUNT_NOT_FOUND),
        UNKNOWN_TARGET(RejectionReason.TARGET_ACCOUNT_NOT_FOUND),
        CURRENCY_MISMATCH(RejectionReason.CURRENCY_MISMATCH);

        private final RejectionReason expectedReason;

        RejectionScenario(RejectionReason expectedReason) {
            this.expectedReason = expectedReason;
        }

        private RejectionReason expectedReason() {
            return expectedReason;
        }
    }
}
