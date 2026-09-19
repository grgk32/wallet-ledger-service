package com.example.walletledger.usecases.api;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateAccountCommandTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final String OWNER_REFERENCE = "customer-1";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("100.00");

    @Test
    void shouldRetainEveryComponentWhenCommandIsCreated() {
        // when
        var command = createAccountCommand();

        // then
        assertThat(command).extracting(CreateAccountCommand::ownerReference, CreateAccountCommand::currency,
                        CreateAccountCommand::initialBalance)
                .containsExactly(OWNER_REFERENCE, EURO, INITIAL_BALANCE);
    }

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingComponents")
    void shouldRejectCommandWhenMandatoryComponentIsNull(String componentName, ThrowingCallable construction) {
        // then
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(componentName);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"0", "0.00", "0.01", "1000000.00"})
    void shouldCarryTheRequestedOpeningBalanceWhenCommandIsCreated(String initialBalance) {
        // when
        var command = new CreateAccountCommand(OWNER_REFERENCE, EURO, new BigDecimal(initialBalance));

        // then
        assertThat(command.initialBalance()).isEqualByComparingTo(initialBalance);
    }

    @Test
    void shouldDeferValidationToTheDomainWhenOpeningBalanceIsNegative() {
        // when
        var command = new CreateAccountCommand(OWNER_REFERENCE, EURO, new BigDecimal("-1.00"));

        // then
        assertThat(command.initialBalance()).isNegative();
    }

    @Test
    void shouldTreatCommandsAsEqualWhenEveryComponentMatches() {
        // then
        assertThat(createAccountCommand()).isEqualTo(createAccountCommand())
                .hasSameHashCodeAs(createAccountCommand());
    }

    private static Stream<Arguments> missingComponents() {
        return Stream.of(
                Arguments.of("ownerReference", (ThrowingCallable) () ->
                        new CreateAccountCommand(null, EURO, INITIAL_BALANCE)),
                Arguments.of("currency", (ThrowingCallable) () ->
                        new CreateAccountCommand(OWNER_REFERENCE, null, INITIAL_BALANCE)),
                Arguments.of("initialBalance", (ThrowingCallable) () ->
                        new CreateAccountCommand(OWNER_REFERENCE, EURO, null)));
    }

    private static CreateAccountCommand createAccountCommand() {
        return new CreateAccountCommand(OWNER_REFERENCE, EURO, INITIAL_BALANCE);
    }
}
