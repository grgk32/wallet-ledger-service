package com.example.walletledger.usecases.service;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.usecases.api.TransferCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class TransferFingerprintTest {

    private static final Currency EURO = Currency.getInstance("EUR");

    @Test
    void shouldProduceEqualFingerprintsWhenPayloadsAreIdentical() {
        var left = command("source", "target", "25.00", "EUR");
        var right = command("source", "target", "25.00", "EUR");

        assertThat(TransferFingerprint.of(left)).isEqualTo(TransferFingerprint.of(right));
    }

    @Test
    void shouldIgnoreTheIdempotencyKeyWhenFingerprintIsComputed() {
        var left = new TransferCommand(IdempotencyKey.of("key-1"), AccountId.of("source"), AccountId.of("target"),
                euros("25.00"));
        var right = new TransferCommand(IdempotencyKey.of("key-2"), AccountId.of("source"), AccountId.of("target"),
                euros("25.00"));

        assertThat(TransferFingerprint.of(left)).isEqualTo(TransferFingerprint.of(right));
    }

    @ParameterizedTest(name = "{index}: {0} {1} {2} {3}")
    @CsvSource({"other,target,25.00,EUR", "source,other,25.00,EUR", "source,target,25.01,EUR",
            "source,target,25.00,USD"})
    void shouldProduceDifferentFingerprintWhenAnyFieldChanges(String sourceAccountId,
                                                              String targetAccountId,
                                                              String amount,
                                                              String currencyCode) {
        var reference = command("source", "target", "25.00", "EUR");
        var altered = command(sourceAccountId, targetAccountId, amount, currencyCode);

        assertThat(TransferFingerprint.of(altered)).isNotEqualTo(TransferFingerprint.of(reference));
    }

    @Test
    void shouldProduceEqualFingerprintsWhenAmountScalesDifferButValuesMatch() {
        var left = command("source", "target", "25", "EUR");
        var right = command("source", "target", "25.00", "EUR");

        assertThat(TransferFingerprint.of(left)).isEqualTo(TransferFingerprint.of(right));
    }

    private static TransferCommand command(String sourceAccountId,
                                           String targetAccountId,
                                           String amount,
                                           String currencyCode) {
        return new TransferCommand(IdempotencyKey.of("key-1"), AccountId.of(sourceAccountId),
                AccountId.of(targetAccountId), Money.of(new BigDecimal(amount), Money.currencyOf(currencyCode)));
    }

    private static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }
}
