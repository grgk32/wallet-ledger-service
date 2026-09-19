package com.example.walletledger.core.model;

import com.example.walletledger.core.exception.CurrencyMismatchException;
import com.example.walletledger.core.exception.InvalidMonetaryAmountException;
import com.example.walletledger.core.exception.UnknownCurrencyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final Currency YEN = Currency.getInstance("JPY");
    private static final Currency DOLLAR = Currency.getInstance("USD");
    private static final Currency DINAR = Currency.getInstance("BHD");
    private static final Currency BULLION = Currency.getInstance("XAU");

    @Test
    void shouldRejectAmountWhenAmountIsNull() {
        assertThatThrownBy(() -> Money.of(null, EURO))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("amount must not be null");
    }

    @Test
    void shouldRejectAmountWhenCurrencyIsNull() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("currency must not be null");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"-0.01", "-1", "-99999999.99"})
    void shouldRejectAmountWhenAmountIsNegative(String negativeAmount) {
        assertThatThrownBy(() -> Money.of(new BigDecimal(negativeAmount), EURO))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("must not be negative");
    }

    @ParameterizedTest(name = "{index}: {0} -> {1}")
    @CsvSource({"1,1.00", "1.5,1.50", "1.50,1.50", "0,0.00", "12345678901234567,12345678901234567.00"})
    void shouldNormaliseScaleWhenCurrencyDefinesFractionDigits(String input, String expected) {
        var money = Money.of(new BigDecimal(input), EURO);

        assertThat(money.amount()).isEqualByComparingTo(expected).hasToString(expected);
    }

    @Test
    void shouldNormaliseScaleWhenCurrencyHasNoFractionDigits() {
        var money = Money.of(new BigDecimal("1200"), YEN);

        assertThat(money.amount().scale()).isZero();
    }

    @ParameterizedTest(name = "{index}: {0} {1}")
    @CsvSource({"0.001,EUR", "1.005,EUR", "0.5,JPY", "1.1,JPY"})
    void shouldRejectAmountWhenPrecisionExceedsCurrencyFractionDigits(String amount, String currencyCode) {
        assertThatThrownBy(() -> Money.of(new BigDecimal(amount), currencyCode))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("fraction digits permitted");
    }

    @Test
    void shouldAddAmountsWhenCurrenciesMatch() {
        var sum = Money.of(new BigDecimal("10.25"), EURO).add(Money.of(new BigDecimal("4.75"), EURO));

        assertThat(sum).isEqualTo(Money.of(new BigDecimal("15.00"), EURO));
    }

    @Test
    void shouldSubtractAmountsWhenCurrenciesMatch() {
        var difference = Money.of(new BigDecimal("10.25"), EURO).subtract(Money.of(new BigDecimal("0.25"), EURO));

        assertThat(difference).isEqualTo(Money.of(new BigDecimal("10.00"), EURO));
    }

    @Test
    void shouldRejectSubtractionWhenResultWouldBeNegative() {
        var balance = Money.of(new BigDecimal("1.00"), EURO);
        var larger = Money.of(new BigDecimal("1.01"), EURO);

        assertThatThrownBy(() -> balance.subtract(larger))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("must not be negative");
    }

    @Test
    void shouldRejectOperationWhenCurrenciesDiffer() {
        var euros = Money.of(new BigDecimal("1.00"), EURO);
        var dollars = Money.of(new BigDecimal("1.00"), DOLLAR);

        assertThatThrownBy(() -> euros.add(dollars))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("expected currency EUR but received USD");
    }

    @Test
    void shouldRejectComparisonWhenOtherAmountIsNull() {
        var euros = Money.of(new BigDecimal("1.00"), EURO);

        assertThatThrownBy(() -> euros.requireSameCurrency(null))
                .isInstanceOf(InvalidMonetaryAmountException.class);
    }

    @ParameterizedTest(name = "{index}: {0} vs {1}")
    @MethodSource("comparisonCases")
    void shouldOrderAmountsWhenComparedWithinOneCurrency(String left, String right, int expectedSignum) {
        var comparison = Money.of(new BigDecimal(left), EURO).compareTo(Money.of(new BigDecimal(right), EURO));

        assertThat(Integer.signum(comparison)).isEqualTo(expectedSignum);
    }

    @Test
    void shouldReportGreaterWhenAmountExceedsOther() {
        var larger = Money.of(new BigDecimal("2.00"), EURO);
        var smaller = Money.of(new BigDecimal("1.99"), EURO);

        assertThat(larger.isGreaterThan(smaller)).isTrue();
    }

    @Test
    void shouldReportLessWhenAmountIsBelowOther() {
        var smaller = Money.of(new BigDecimal("1.99"), EURO);
        var larger = Money.of(new BigDecimal("2.00"), EURO);

        assertThat(smaller.isLessThan(larger)).isTrue();
    }

    @Test
    void shouldReportZeroWhenAmountIsZero() {
        assertThat(Money.zero(EURO).isZero()).isTrue();
    }

    @Test
    void shouldReportNotPositiveWhenAmountIsZero() {
        assertThat(Money.zero(EURO).isPositive()).isFalse();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "EU", "EURO", "123", "zz"})
    void shouldRejectCurrencyWhenCodeIsNotIso4217(String currencyCode) {
        assertThatThrownBy(() -> Money.currencyOf(currencyCode)).isInstanceOf(UnknownCurrencyException.class);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"eur", "EUR", " eur ", "Eur"})
    void shouldResolveCurrencyWhenCodeIsIso4217InAnyCase(String currencyCode) {
        assertThat(Money.currencyOf(currencyCode)).isEqualTo(EURO);
    }

    @Test
    void shouldCarryVeryLargeAmountsWhenWithinBigDecimalRange() {
        var huge = new BigDecimal(Long.MAX_VALUE).add(BigDecimal.ONE);

        assertThat(Money.of(huge, EURO).amount()).isEqualByComparingTo(huge);
    }

    @Test
    void shouldRenderAmountAndCurrencyWhenConvertedToString() {
        assertThat(Money.of(new BigDecimal("25.5"), EURO)).hasToString("25.50 EUR");
    }

    @Test
    void shouldResolveCurrencyWhenFactoryTakesCurrencyCode() {
        assertThat(Money.of(new BigDecimal("1.00"), "EUR")).isEqualTo(Money.of(new BigDecimal("1.00"), EURO));
    }

    @Test
    void shouldRejectAmountWhenCurrencyCodeIsUnknown() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "XYZ")).isInstanceOf(UnknownCurrencyException.class);
    }

    @Test
    void shouldStartAtZeroWhenMoneyIsCreatedForACurrency() {
        var zero = Money.zero(EURO);

        assertThat(zero.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldTreatAmountsAsEqualWhenScalesDifferButValuesMatch() {
        var withoutFraction = Money.of(new BigDecimal("1"), EURO);
        var withFraction = Money.of(new BigDecimal("1.00"), EURO);

        assertThat(withoutFraction).isEqualTo(withFraction).hasSameHashCodeAs(withFraction);
    }

    @Test
    void shouldRejectAdditionWhenOtherAmountIsNull() {
        var euros = Money.of(new BigDecimal("1.00"), EURO);

        assertThatThrownBy(() -> euros.add(null)).isInstanceOf(InvalidMonetaryAmountException.class);
    }

    @Test
    void shouldRejectSubtractionWhenOtherAmountIsNull() {
        var euros = Money.of(new BigDecimal("1.00"), EURO);

        assertThatThrownBy(() -> euros.subtract(null)).isInstanceOf(InvalidMonetaryAmountException.class);
    }

    @Test
    void shouldRejectGreaterThanWhenCurrenciesDiffer() {
        var euros = Money.of(new BigDecimal("2.00"), EURO);
        var dollars = Money.of(new BigDecimal("1.00"), DOLLAR);

        assertThatThrownBy(() -> euros.isGreaterThan(dollars)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void shouldRejectLessThanWhenCurrenciesDiffer() {
        var euros = Money.of(new BigDecimal("1.00"), EURO);
        var dollars = Money.of(new BigDecimal("2.00"), DOLLAR);

        assertThatThrownBy(() -> euros.isLessThan(dollars)).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void shouldAcceptThreeFractionDigitsWhenCurrencyDefinesThem() {
        var dinars = Money.of(new BigDecimal("1.234"), DINAR);

        assertThat(dinars.amount().scale()).isEqualTo(3);
    }

    @Test
    void shouldRejectFourFractionDigitsWhenCurrencyDefinesThree() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("1.2345"), DINAR))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("3 fraction digits");
    }

    @Test
    void shouldNormaliseToWholeUnitsWhenCurrencyDeclaresNoFractionDigits() {
        var gold = Money.of(new BigDecimal("5"), BULLION);

        assertThat(gold.amount().scale()).isZero();
    }

    @Test
    void shouldRejectFractionsWhenCurrencyDeclaresNoFractionDigits() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("5.5"), BULLION))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("0 fraction digits");
    }

    @Test
    void shouldReportPositiveWhenAmountIsAboveZero() {
        assertThat(Money.of(new BigDecimal("0.01"), EURO).isPositive()).isTrue();
    }

    @Test
    void shouldReportNotZeroWhenAmountIsAboveZero() {
        assertThat(Money.of(new BigDecimal("0.01"), EURO).isZero()).isFalse();
    }

    @Test
    void shouldLeaveTheReceiverUnchangedWhenAmountsAreAdded() {
        var balance = Money.of(new BigDecimal("10.00"), EURO);

        balance.add(Money.of(new BigDecimal("5.00"), EURO));

        assertThat(balance.amount()).isEqualByComparingTo("10.00");
    }

    private static Stream<Arguments> comparisonCases() {
        return Stream.of(
                Arguments.of("1.00", "1.000", 0),
                Arguments.of("1.00", "2.00", -1),
                Arguments.of("2.00", "1.00", 1),
                Arguments.of("0", "0.00", 0));
    }
}
