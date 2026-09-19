package com.example.walletledger.usecases.api;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.Money;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferCommandTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final IdempotencyKey IDEMPOTENCY_KEY = IdempotencyKey.of("key-1");
    private static final AccountId SOURCE_ID = AccountId.of("source");
    private static final AccountId TARGET_ID = AccountId.of("target");
    private static final Money AMOUNT = Money.of(new BigDecimal("25.00"), EURO);

    @Test
    void shouldRetainEveryComponentWhenCommandIsCreated() {
        // when
        var command = transferCommand();

        // then
        assertThat(command).extracting(TransferCommand::idempotencyKey, TransferCommand::sourceAccountId,
                        TransferCommand::targetAccountId, TransferCommand::amount)
                .containsExactly(IDEMPOTENCY_KEY, SOURCE_ID, TARGET_ID, AMOUNT);
    }

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingComponents")
    void shouldRejectCommandWhenMandatoryComponentIsNull(String componentName, ThrowingCallable construction) {
        // then
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(componentName);
    }

    @Test
    void shouldAcceptCommandWhenSourceAndTargetAreIdentical() {
        // when
        var command = new TransferCommand(IDEMPOTENCY_KEY, SOURCE_ID, SOURCE_ID, AMOUNT);

        // then
        assertThat(command.sourceAccountId()).isEqualTo(command.targetAccountId());
    }

    @Test
    void shouldTreatCommandsAsEqualWhenEveryComponentMatches() {
        // then
        assertThat(transferCommand()).isEqualTo(transferCommand()).hasSameHashCodeAs(transferCommand());
    }

    @Test
    void shouldTreatCommandsAsDifferentWhenIdempotencyKeysDiffer() {
        // given
        var other = new TransferCommand(IdempotencyKey.of("key-2"), SOURCE_ID, TARGET_ID, AMOUNT);

        // then
        assertThat(transferCommand()).isNotEqualTo(other);
    }

    private static Stream<Arguments> missingComponents() {
        return Stream.of(
                Arguments.of("idempotencyKey", (ThrowingCallable) () ->
                        new TransferCommand(null, SOURCE_ID, TARGET_ID, AMOUNT)),
                Arguments.of("sourceAccountId", (ThrowingCallable) () ->
                        new TransferCommand(IDEMPOTENCY_KEY, null, TARGET_ID, AMOUNT)),
                Arguments.of("targetAccountId", (ThrowingCallable) () ->
                        new TransferCommand(IDEMPOTENCY_KEY, SOURCE_ID, null, AMOUNT)),
                Arguments.of("amount", (ThrowingCallable) () ->
                        new TransferCommand(IDEMPOTENCY_KEY, SOURCE_ID, TARGET_ID, null)));
    }

    private static TransferCommand transferCommand() {
        return new TransferCommand(IDEMPOTENCY_KEY, SOURCE_ID, TARGET_ID, AMOUNT);
    }
}
