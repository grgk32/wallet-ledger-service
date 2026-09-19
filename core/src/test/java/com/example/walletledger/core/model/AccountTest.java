package com.example.walletledger.core.model;

import com.example.walletledger.core.exception.CurrencyMismatchException;
import com.example.walletledger.core.exception.InsufficientFundsException;
import com.example.walletledger.core.exception.InvalidIdentifierException;
import com.example.walletledger.core.exception.InvalidMonetaryAmountException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final Currency DOLLAR = Currency.getInstance("USD");
    private static final Instant OPENED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void shouldReduceBalanceWhenWithdrawalIsCovered() {
        var account = accountWith("100.00");

        var debited = account.withdraw(euros("25.00"));

        assertThat(debited.balance()).isEqualTo(euros("75.00"));
    }

    @Test
    void shouldReachZeroWhenWithdrawalEqualsBalance() {
        var account = accountWith("100.00");

        var debited = account.withdraw(euros("100.00"));

        assertThat(debited.balance().isZero()).isTrue();
    }

    @Test
    void shouldRejectWithdrawalWhenBalanceIsInsufficient() {
        var account = accountWith("10.00");

        assertThatThrownBy(() -> account.withdraw(euros("10.01")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("cannot release 10.01 EUR");
    }

    @Test
    void shouldRejectWithdrawalWhenAmountIsNull() {
        var account = accountWith("10.00");

        assertThatThrownBy(() -> account.withdraw(null)).isInstanceOf(InvalidMonetaryAmountException.class);
    }

    @Test
    void shouldRejectWithdrawalWhenAmountIsZero() {
        var account = accountWith("10.00");

        assertThatThrownBy(() -> account.withdraw(Money.zero(EURO)))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    void shouldRejectWithdrawalWhenCurrencyDiffers() {
        var account = accountWith("10.00");

        assertThatThrownBy(() -> account.withdraw(Money.of(new BigDecimal("1.00"), DOLLAR)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void shouldIncreaseBalanceWhenDepositIsPositive() {
        var account = accountWith("100.00");

        var credited = account.deposit(euros("0.01"));

        assertThat(credited.balance()).isEqualTo(euros("100.01"));
    }

    @Test
    void shouldRejectDepositWhenAmountIsZero() {
        var account = accountWith("100.00");

        assertThatThrownBy(() -> account.deposit(Money.zero(EURO)))
                .isInstanceOf(InvalidMonetaryAmountException.class);
    }

    @Test
    void shouldRejectDepositWhenCurrencyDiffers() {
        var account = accountWith("100.00");

        assertThatThrownBy(() -> account.deposit(Money.of(new BigDecimal("1.00"), DOLLAR)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void shouldLeaveReceiverUnchangedWhenWithdrawalSucceeds() {
        var account = accountWith("100.00");

        account.withdraw(euros("25.00"));

        assertThat(account.balance()).isEqualTo(euros("100.00"));
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void shouldRejectAccountWhenOwnerReferenceIsBlank(String ownerReference) {
        assertThatThrownBy(() -> new Account(AccountId.of("a-1"), ownerReference, euros("1.00"), OPENED_AT))
                .isInstanceOf(InvalidIdentifierException.class);
    }

    @Test
    void shouldRejectAccountWhenOwnerReferenceExceedsMaximumLength() {
        var tooLong = "o".repeat(129);

        assertThatThrownBy(() -> new Account(AccountId.of("a-1"), tooLong, euros("1.00"), OPENED_AT))
                .isInstanceOf(InvalidIdentifierException.class)
                .hasMessageContaining("128");
    }

    @Test
    void shouldTrimOwnerReferenceWhenSurroundedByWhitespace() {
        var account = new Account(AccountId.of("a-1"), "  customer-1  ", euros("1.00"), OPENED_AT);

        assertThat(account.ownerReference()).isEqualTo("customer-1");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"客户-4711", "Ünïcödé", "владелец", "emoji-💰"})
    void shouldAcceptOwnerReferenceWhenItContainsUnicode(String ownerReference) {
        var account = new Account(AccountId.of("a-1"), ownerReference, euros("1.00"), OPENED_AT);

        assertThat(account.ownerReference()).isEqualTo(ownerReference);
    }

    @Test
    void shouldExposeBalanceCurrencyWhenAccountIsQueried() {
        assertThat(accountWith("1.00").currency()).isEqualTo(EURO);
    }

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingComponents")
    void shouldRejectAccountWhenMandatoryComponentIsNull(String componentName, ThrowingCallable construction) {
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(componentName);
    }

    @Test
    void shouldRejectDepositWhenAmountIsNull() {
        var account = accountWith("100.00");

        assertThatThrownBy(() -> account.deposit(null)).isInstanceOf(InvalidMonetaryAmountException.class);
    }

    @Test
    void shouldAcceptAccountWhenOwnerReferenceLengthIsAtTheMaximum() {
        var atLimit = "o".repeat(128);

        var account = new Account(AccountId.of("a-1"), atLimit, euros("1.00"), OPENED_AT);

        assertThat(account.ownerReference()).hasSize(128);
    }

    @Test
    void shouldKeepIdentityWhenWithdrawalSucceeds() {
        var account = accountWith("100.00");

        var debited = account.withdraw(euros("25.00"));

        assertThat(debited).extracting(Account::accountId, Account::ownerReference, Account::openedAt)
                .containsExactly(account.accountId(), account.ownerReference(), account.openedAt());
    }

    @Test
    void shouldKeepIdentityWhenDepositSucceeds() {
        var account = accountWith("100.00");

        var credited = account.deposit(euros("25.00"));

        assertThat(credited).extracting(Account::accountId, Account::ownerReference, Account::openedAt)
                .containsExactly(account.accountId(), account.ownerReference(), account.openedAt());
    }

    @Test
    void shouldLeaveReceiverUnchangedWhenDepositSucceeds() {
        var account = accountWith("100.00");

        account.deposit(euros("25.00"));

        assertThat(account.balance()).isEqualTo(euros("100.00"));
    }

    @Test
    void shouldReleaseTheSmallestUnitWhenBalanceCoversIt() {
        var account = accountWith("0.01");

        var debited = account.withdraw(euros("0.01"));

        assertThat(debited.balance().isZero()).isTrue();
    }

    @Test
    void shouldRejectWithdrawalWhenBalanceIsZero() {
        var account = accountWith("0.00");

        assertThatThrownBy(() -> account.withdraw(euros("0.01"))).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldTreatAccountsAsEqualWhenEveryComponentMatches() {
        assertThat(accountWith("100.00")).isEqualTo(accountWith("100.00")).hasSameHashCodeAs(accountWith("100.00"));
    }

    @Test
    void shouldTreatAccountsAsDifferentWhenBalancesDiffer() {
        assertThat(accountWith("100.00")).isNotEqualTo(accountWith("99.00"));
    }

    private static Stream<Arguments> missingComponents() {
        return Stream.of(
                Arguments.of("accountId", (ThrowingCallable) () ->
                        new Account(null, "customer-1", euros("1.00"), OPENED_AT)),
                Arguments.of("balance", (ThrowingCallable) () ->
                        new Account(AccountId.of("a-1"), "customer-1", null, OPENED_AT)),
                Arguments.of("openedAt", (ThrowingCallable) () ->
                        new Account(AccountId.of("a-1"), "customer-1", euros("1.00"), null)));
    }

    private static Account accountWith(String balance) {
        return new Account(AccountId.of("account-1"), "customer-1", euros(balance), OPENED_AT);
    }

    private static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }
}
