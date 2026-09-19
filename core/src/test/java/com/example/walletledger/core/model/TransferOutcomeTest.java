package com.example.walletledger.core.model;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferOutcomeTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final TransferId TRANSFER_ID = TransferId.of("transfer-1");
    private static final AccountId SOURCE_ID = AccountId.of("source");
    private static final AccountId TARGET_ID = AccountId.of("target");
    private static final Money AMOUNT = Money.of(new BigDecimal("25.00"), EURO);
    private static final Instant POSTED_AT = Instant.parse("2026-03-04T10:15:30Z");

    @Test
    void shouldRetainEveryComponentWhenTransferIsApplied() {
        // when
        var applied = appliedOutcome();

        // then
        assertThat(applied).extracting(TransferOutcome.Applied::transferId,
                        TransferOutcome.Applied::sourceAccountId, TransferOutcome.Applied::targetAccountId,
                        TransferOutcome.Applied::amount, TransferOutcome.Applied::postedAt)
                .containsExactly(TRANSFER_ID, SOURCE_ID, TARGET_ID, AMOUNT, POSTED_AT);
    }

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingAppliedComponents")
    void shouldRejectAppliedOutcomeWhenMandatoryComponentIsNull(String componentName, ThrowingCallable construction) {
        // then
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(componentName);
    }

    @Test
    void shouldRejectRejectionWhenReasonIsNull() {
        // then
        assertThatThrownBy(() -> new TransferOutcome.Rejected(null, "detail"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void shouldKeepTheSuppliedDetailWhenRejectionCarriesOne() {
        // when
        var rejected = new TransferOutcome.Rejected(RejectionReason.INSUFFICIENT_FUNDS, "balance too low");

        // then
        assertThat(rejected.detail()).isEqualTo("balance too low");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RejectionReason.class)
    void shouldFallBackToTheReasonNameWhenRejectionDetailIsNull(RejectionReason reason) {
        // when
        var rejected = new TransferOutcome.Rejected(reason, null);

        // then
        assertThat(rejected.detail()).isEqualTo(reason.name());
    }

    @Test
    void shouldRemainDistinguishableWhenOutcomeIsInspectedByType() {
        // given
        TransferOutcome applied = appliedOutcome();
        TransferOutcome rejected = new TransferOutcome.Rejected(RejectionReason.CURRENCY_MISMATCH, "EUR against USD");

        // then
        assertThat(describe(applied)).isEqualTo("applied:transfer-1");
        assertThat(describe(rejected)).isEqualTo("rejected:CURRENCY_MISMATCH");
    }

    @Test
    void shouldPermitApplicationAndRejectionOnlyWhenHierarchyIsInspected() {
        // then
        assertThat(TransferOutcome.class.isSealed()).isTrue();
        assertThat(TransferOutcome.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(TransferOutcome.Applied.class, TransferOutcome.Rejected.class);
    }

    @Test
    void shouldTreatOutcomesAsEqualWhenEveryComponentMatches() {
        // then
        assertThat(appliedOutcome()).isEqualTo(appliedOutcome()).hasSameHashCodeAs(appliedOutcome());
    }

    private static String describe(TransferOutcome outcome) {
        if (outcome instanceof TransferOutcome.Applied applied) {
            return "applied:" + applied.transferId().value();
        }
        if (outcome instanceof TransferOutcome.Rejected rejected) {
            return "rejected:" + rejected.reason().name();
        }
        return "unknown";
    }

    private static Stream<Arguments> missingAppliedComponents() {
        return Stream.of(
                Arguments.of("transferId", (ThrowingCallable) () ->
                        new TransferOutcome.Applied(null, SOURCE_ID, TARGET_ID, AMOUNT, POSTED_AT)),
                Arguments.of("sourceAccountId", (ThrowingCallable) () ->
                        new TransferOutcome.Applied(TRANSFER_ID, null, TARGET_ID, AMOUNT, POSTED_AT)),
                Arguments.of("targetAccountId", (ThrowingCallable) () ->
                        new TransferOutcome.Applied(TRANSFER_ID, SOURCE_ID, null, AMOUNT, POSTED_AT)),
                Arguments.of("amount", (ThrowingCallable) () ->
                        new TransferOutcome.Applied(TRANSFER_ID, SOURCE_ID, TARGET_ID, null, POSTED_AT)),
                Arguments.of("postedAt", (ThrowingCallable) () ->
                        new TransferOutcome.Applied(TRANSFER_ID, SOURCE_ID, TARGET_ID, AMOUNT, null)));
    }

    private static TransferOutcome.Applied appliedOutcome() {
        return new TransferOutcome.Applied(TRANSFER_ID, SOURCE_ID, TARGET_ID, AMOUNT, POSTED_AT);
    }
}
