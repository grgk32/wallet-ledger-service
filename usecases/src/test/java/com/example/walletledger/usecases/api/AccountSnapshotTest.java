package com.example.walletledger.usecases.api;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.Money;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

class AccountSnapshotTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final AccountId ACCOUNT_ID = AccountId.of("account-1");
    private static final String OWNER_REFERENCE = "customer-1";
    private static final Money BALANCE = Money.of(new BigDecimal("42.00"), EURO);
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-07T07:07:07Z");

    @Test
    void shouldRetainEveryComponentWhenSnapshotIsCreated() {
        // when
        var snapshot = accountSnapshot();

        // then
        assertThat(snapshot).extracting(AccountSnapshot::accountId, AccountSnapshot::ownerReference,
                        AccountSnapshot::balance, AccountSnapshot::observedAt)
                .containsExactly(ACCOUNT_ID, OWNER_REFERENCE, BALANCE, OBSERVED_AT);
    }

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingComponents")
    void shouldRejectSnapshotWhenMandatoryComponentIsNull(String componentName, ThrowingCallable construction) {
        // then
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(componentName);
    }

    @Test
    void shouldAcceptSnapshotWhenBalanceIsZero() {
        // when
        var snapshot = new AccountSnapshot(ACCOUNT_ID, OWNER_REFERENCE, Money.zero(EURO), OBSERVED_AT);

        // then
        assertThat(snapshot.balance().isZero()).isTrue();
    }

    @Test
    void shouldTreatSnapshotsAsEqualWhenEveryComponentMatches() {
        // then
        assertThat(accountSnapshot()).isEqualTo(accountSnapshot()).hasSameHashCodeAs(accountSnapshot());
    }

    @Test
    void shouldTreatSnapshotsAsDifferentWhenObservationInstantsDiffer() {
        // given
        var later = new AccountSnapshot(ACCOUNT_ID, OWNER_REFERENCE, BALANCE, OBSERVED_AT.plusSeconds(1));

        // then
        assertThat(accountSnapshot()).isNotEqualTo(later);
    }

    private static Stream<Arguments> missingComponents() {
        return Stream.of(
                Arguments.of("accountId", (ThrowingCallable) () ->
                        new AccountSnapshot(null, OWNER_REFERENCE, BALANCE, OBSERVED_AT)),
                Arguments.of("ownerReference", (ThrowingCallable) () ->
                        new AccountSnapshot(ACCOUNT_ID, null, BALANCE, OBSERVED_AT)),
                Arguments.of("balance", (ThrowingCallable) () ->
                        new AccountSnapshot(ACCOUNT_ID, OWNER_REFERENCE, null, OBSERVED_AT)),
                Arguments.of("observedAt", (ThrowingCallable) () ->
                        new AccountSnapshot(ACCOUNT_ID, OWNER_REFERENCE, BALANCE, null)));
    }

    private static AccountSnapshot accountSnapshot() {
        return new AccountSnapshot(ACCOUNT_ID, OWNER_REFERENCE, BALANCE, OBSERVED_AT);
    }
}
