package com.example.walletledger.usecases.spi;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.RejectionReason;
import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.core.model.TransferOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimResultTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final Instant POSTED_AT = Instant.parse("2026-06-06T09:00:00Z");

    @Test
    void shouldCarryTheSettledOutcomeWhenClaimIsReplayed() {
        // given
        var outcome = appliedOutcome();

        // when
        var replayed = new ClaimResult.Replayed(outcome);

        // then
        assertThat(replayed.outcome()).isEqualTo(outcome);
    }

    @Test
    void shouldRejectReplayWhenOutcomeIsNull() {
        // then
        assertThatThrownBy(() -> new ClaimResult.Replayed(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("outcome");
    }

    @Test
    void shouldCarryARejectionWhenTheOriginalRequestWasRefused() {
        // given
        var outcome = new TransferOutcome.Rejected(RejectionReason.INSUFFICIENT_FUNDS, "balance too low");

        // when
        var replayed = new ClaimResult.Replayed(outcome);

        // then
        assertThat(replayed.outcome()).isInstanceOf(TransferOutcome.Rejected.class);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("markerResults")
    void shouldTreatMarkerResultsAsEqualWhenTypesMatch(String resultName, ClaimResult left, ClaimResult right) {
        // then
        assertThat(left).isEqualTo(right).hasSameHashCodeAs(right);
    }

    @Test
    void shouldTreatMarkerResultsAsDifferentWhenTypesDiffer() {
        // then
        assertThat(new ClaimResult.Claimed())
                .isNotEqualTo(new ClaimResult.InFlight())
                .isNotEqualTo(new ClaimResult.Conflicting());
    }

    @Test
    void shouldPermitFourOutcomesOnlyWhenHierarchyIsInspected() {
        // then
        assertThat(ClaimResult.class.isSealed()).isTrue();
        assertThat(ClaimResult.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(ClaimResult.Claimed.class, ClaimResult.Replayed.class,
                        ClaimResult.InFlight.class, ClaimResult.Conflicting.class);
    }

    @Test
    void shouldRemainDistinguishableWhenResultIsInspectedByType() {
        // then
        assertThat(describe(new ClaimResult.Claimed())).isEqualTo("claimed");
        assertThat(describe(new ClaimResult.Replayed(appliedOutcome()))).isEqualTo("replayed");
        assertThat(describe(new ClaimResult.InFlight())).isEqualTo("in-flight");
        assertThat(describe(new ClaimResult.Conflicting())).isEqualTo("conflicting");
    }

    private static String describe(ClaimResult claimResult) {
        if (claimResult instanceof ClaimResult.Claimed) {
            return "claimed";
        }
        if (claimResult instanceof ClaimResult.Replayed) {
            return "replayed";
        }
        if (claimResult instanceof ClaimResult.InFlight) {
            return "in-flight";
        }
        return "conflicting";
    }

    private static Stream<Arguments> markerResults() {
        return Stream.of(
                Arguments.of("claimed", new ClaimResult.Claimed(), new ClaimResult.Claimed()),
                Arguments.of("in flight", new ClaimResult.InFlight(), new ClaimResult.InFlight()),
                Arguments.of("conflicting", new ClaimResult.Conflicting(), new ClaimResult.Conflicting()));
    }

    private static TransferOutcome appliedOutcome() {
        return new TransferOutcome.Applied(TransferId.of("transfer-1"), AccountId.of("source"),
                AccountId.of("target"), Money.of(new BigDecimal("25.00"), EURO), POSTED_AT);
    }
}
