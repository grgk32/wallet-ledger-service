package com.example.walletledger.core.exception;

import com.example.walletledger.core.model.RejectionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerDomainExceptionTest {

    @ParameterizedTest(name = "{index}: {0} -> {1}")
    @MethodSource("domainFailures")
    void shouldCarryTheRejectionReasonWhenDomainRuleIsViolated(String failureName,
                                                               RejectionReason expectedReason,
                                                               LedgerDomainException exception) {
        // then
        assertThat(exception.rejectionReason()).isEqualTo(expectedReason);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("domainFailures")
    void shouldStayUncheckedWhenDomainRuleIsViolated(String failureName,
                                                     RejectionReason expectedReason,
                                                     LedgerDomainException exception) {
        // then
        assertThat(exception).isInstanceOf(RuntimeException.class).isInstanceOf(LedgerDomainException.class);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("domainFailures")
    void shouldExplainTheViolationWhenDomainRuleIsViolated(String failureName,
                                                           RejectionReason expectedReason,
                                                           LedgerDomainException exception) {
        // then
        assertThat(exception.getMessage()).isNotBlank();
    }

    @Test
    void shouldNameTheAccountAndBothAmountsWhenFundsAreInsufficient() {
        // given
        var exception = new InsufficientFundsException("account-1", "10.00 EUR", "25.00 EUR");

        // then
        assertThat(exception).hasMessage("account account-1 holds 10.00 EUR and cannot release 25.00 EUR");
    }

    @Test
    void shouldNameBothCurrenciesWhenCurrenciesDoNotMatch() {
        // given
        var exception = new CurrencyMismatchException("EUR", "USD");

        // then
        assertThat(exception).hasMessage("expected currency EUR but received USD");
    }

    @Test
    void shouldNameTheAccountWhenSourceAndTargetAreIdentical() {
        // given
        var exception = new SameAccountTransferException("account-1");

        // then
        assertThat(exception).hasMessageContaining("account-1");
    }

    @Test
    void shouldNameTheCodeWhenCurrencyIsNotIso4217() {
        // given
        var exception = new UnknownCurrencyException("EURO");

        // then
        assertThat(exception).hasMessage("unknown ISO 4217 currency code: EURO");
    }

    @Test
    void shouldCoverEveryRejectionReasonWhenDomainFailuresAreEnumerated() {
        // given
        var coveredReasons = domainFailures().map(arguments -> arguments.get()[1]).toList();

        // then
        assertThat(coveredReasons).containsAll(List.of(RejectionReason.INSUFFICIENT_FUNDS,
                RejectionReason.CURRENCY_MISMATCH, RejectionReason.SAME_ACCOUNT_TRANSFER,
                RejectionReason.INVALID_MONETARY_AMOUNT, RejectionReason.INVALID_IDENTIFIER,
                RejectionReason.UNKNOWN_CURRENCY));
    }

    private static Stream<Arguments> domainFailures() {
        return Stream.of(
                Arguments.of("insufficient funds", RejectionReason.INSUFFICIENT_FUNDS,
                        new InsufficientFundsException("account-1", "10.00 EUR", "25.00 EUR")),
                Arguments.of("currency mismatch", RejectionReason.CURRENCY_MISMATCH,
                        new CurrencyMismatchException("EUR", "USD")),
                Arguments.of("same account transfer", RejectionReason.SAME_ACCOUNT_TRANSFER,
                        new SameAccountTransferException("account-1")),
                Arguments.of("invalid monetary amount", RejectionReason.INVALID_MONETARY_AMOUNT,
                        new InvalidMonetaryAmountException("amount must not be negative")),
                Arguments.of("invalid identifier", RejectionReason.INVALID_IDENTIFIER,
                        new InvalidIdentifierException("accountId must not be blank")),
                Arguments.of("unknown currency", RejectionReason.UNKNOWN_CURRENCY,
                        new UnknownCurrencyException("EURO")));
    }
}
