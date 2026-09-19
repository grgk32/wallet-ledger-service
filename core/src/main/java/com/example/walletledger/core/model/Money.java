package com.example.walletledger.core.model;

import com.example.walletledger.core.exception.CurrencyMismatchException;
import com.example.walletledger.core.exception.InvalidMonetaryAmountException;
import com.example.walletledger.core.exception.UnknownCurrencyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        if (currency == null) {
            throw new InvalidMonetaryAmountException("currency must not be null");
        }
        if (amount == null) {
            throw new InvalidMonetaryAmountException("amount must not be null");
        }
        amount = normalizeScale(amount, currency);
        if (amount.signum() < 0) {
            throw new InvalidMonetaryAmountException("amount must not be negative but was " + amount.toPlainString());
        }
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyOf(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Currency currencyOf(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new UnknownCurrencyException(String.valueOf(currencyCode));
        }
        try {
            return Currency.getInstance(currencyCode.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownCode) {
            throw new UnknownCurrencyException(currencyCode);
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public void requireSameCurrency(Money other) {
        if (other == null) {
            throw new InvalidMonetaryAmountException("amount must not be null");
        }
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency.getCurrencyCode(), other.currency.getCurrencyCode());
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }

    private static BigDecimal normalizeScale(BigDecimal candidate, Currency currency) {
        var fractionDigits = Math.max(currency.getDefaultFractionDigits(), 0);
        try {
            return candidate.setScale(fractionDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException excessivePrecision) {
            throw new InvalidMonetaryAmountException("amount " + candidate.toPlainString()
                    + " exceeds the " + fractionDigits + " fraction digits permitted for "
                    + currency.getCurrencyCode());
        }
    }
}
