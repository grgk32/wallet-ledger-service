package com.example.walletledger.usecases.api;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferReceiptTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final TransferId TRANSFER_ID = TransferId.of("transfer-1");
    private static final AccountId SOURCE_ID = AccountId.of("source");
    private static final AccountId TARGET_ID = AccountId.of("target");
    private static final Money AMOUNT = Money.of(new BigDecimal("25.00"), EURO);
    private static final Instant POSTED_AT = Instant.parse("2026-06-06T09:00:00Z");

    @Test
    void shouldRetainEveryComponentWhenReceiptIsCreated() {
        // when
        var receipt = receiptWith(false);

        // then
        assertThat(receipt).extracting(TransferReceipt::transferId, TransferReceipt::sourceAccountId,
                        TransferReceipt::targetAccountId, TransferReceipt::amount, TransferReceipt::postedAt,
                        TransferReceipt::replayed)
                .containsExactly(TRANSFER_ID, SOURCE_ID, TARGET_ID, AMOUNT, POSTED_AT, false);
    }

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingComponents")
    void shouldRejectReceiptWhenMandatoryComponentIsNull(String componentName, ThrowingCallable construction) {
        // then
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(componentName);
    }

    @ParameterizedTest(name = "{index}: replayed={0}")
    @ValueSource(booleans = {true, false})
    void shouldReportWhetherTheOutcomeWasReplayedWhenReceiptIsRead(boolean replayed) {
        // when
        var receipt = receiptWith(replayed);

        // then
        assertThat(receipt.replayed()).isEqualTo(replayed);
    }

    @Test
    void shouldTreatReceiptsAsDifferentWhenOnlyTheReplayFlagDiffers() {
        // then
        assertThat(receiptWith(true)).isNotEqualTo(receiptWith(false));
    }

    @Test
    void shouldTreatReceiptsAsEqualWhenEveryComponentMatches() {
        // then
        assertThat(receiptWith(true)).isEqualTo(receiptWith(true)).hasSameHashCodeAs(receiptWith(true));
    }

    private static Stream<Arguments> missingComponents() {
        return Stream.of(
                Arguments.of("transferId", (ThrowingCallable) () ->
                        new TransferReceipt(null, SOURCE_ID, TARGET_ID, AMOUNT, POSTED_AT, false)),
                Arguments.of("sourceAccountId", (ThrowingCallable) () ->
                        new TransferReceipt(TRANSFER_ID, null, TARGET_ID, AMOUNT, POSTED_AT, false)),
                Arguments.of("targetAccountId", (ThrowingCallable) () ->
                        new TransferReceipt(TRANSFER_ID, SOURCE_ID, null, AMOUNT, POSTED_AT, false)),
                Arguments.of("amount", (ThrowingCallable) () ->
                        new TransferReceipt(TRANSFER_ID, SOURCE_ID, TARGET_ID, null, POSTED_AT, false)),
                Arguments.of("postedAt", (ThrowingCallable) () ->
                        new TransferReceipt(TRANSFER_ID, SOURCE_ID, TARGET_ID, AMOUNT, null, false)));
    }

    private static TransferReceipt receiptWith(boolean replayed) {
        return new TransferReceipt(TRANSFER_ID, SOURCE_ID, TARGET_ID, AMOUNT, POSTED_AT, replayed);
    }
}
