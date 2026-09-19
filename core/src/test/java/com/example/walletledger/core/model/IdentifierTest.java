package com.example.walletledger.core.model;

import com.example.walletledger.core.exception.InvalidIdentifierException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierTest {

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldRejectAccountIdWhenValueIsBlank(String value) {
        assertThatThrownBy(() -> AccountId.of(value)).isInstanceOf(InvalidIdentifierException.class);
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldRejectTransferIdWhenValueIsBlank(String value) {
        assertThatThrownBy(() -> TransferId.of(value)).isInstanceOf(InvalidIdentifierException.class);
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldRejectIdempotencyKeyWhenValueIsBlank(String value) {
        assertThatThrownBy(() -> IdempotencyKey.of(value)).isInstanceOf(InvalidIdentifierException.class);
    }

    @Test
    void shouldRejectAccountIdWhenValueExceedsMaximumLength() {
        assertThatThrownBy(() -> AccountId.of("a".repeat(65)))
                .isInstanceOf(InvalidIdentifierException.class)
                .hasMessageContaining("64");
    }

    @Test
    void shouldRejectIdempotencyKeyWhenValueExceedsMaximumLength() {
        assertThatThrownBy(() -> IdempotencyKey.of("k".repeat(129)))
                .isInstanceOf(InvalidIdentifierException.class)
                .hasMessageContaining("128");
    }

    @Test
    void shouldTrimAccountIdWhenValueHasSurroundingWhitespace() {
        assertThat(AccountId.of("  account-1  ").value()).isEqualTo("account-1");
    }

    @Test
    void shouldOrderAccountIdsWhenSortedLexicographically() {
        var identifiers = List.of(AccountId.of("c"), AccountId.of("a"), AccountId.of("b"));

        assertThat(identifiers.stream().sorted().toList())
                .containsExactly(AccountId.of("a"), AccountId.of("b"), AccountId.of("c"));
    }

    @Test
    void shouldRecogniseFundingSourceWhenIdentifierIsReserved() {
        assertThat(AccountId.externalFundingSource().isExternalFundingSource()).isTrue();
    }

    @Test
    void shouldNotRecogniseFundingSourceWhenIdentifierIsOrdinary() {
        assertThat(AccountId.of("account-1").isExternalFundingSource()).isFalse();
    }

    @Test
    void shouldRenderRawValueWhenIdentifierConvertedToString() {
        assertThat(TransferId.of("transfer-1")).hasToString("transfer-1");
    }

    @Test
    void shouldRenderRawValueWhenIdempotencyKeyConvertedToString() {
        assertThat(IdempotencyKey.of("key-1")).hasToString("key-1");
    }
    @Test
    void shouldAcceptAccountIdWhenLengthIsAtTheMaximum() {
        assertThat(AccountId.of("a".repeat(64)).value()).hasSize(64);
    }

    @Test
    void shouldAcceptIdempotencyKeyWhenLengthIsAtTheMaximum() {
        assertThat(IdempotencyKey.of("k".repeat(128)).value()).hasSize(128);
    }

    @Test
    void shouldRejectTransferIdWhenValueExceedsMaximumLength() {
        assertThatThrownBy(() -> TransferId.of("t".repeat(65)))
                .isInstanceOf(InvalidIdentifierException.class)
                .hasMessageContaining("64");
    }

    @Test
    void shouldTrimTransferIdWhenValueHasSurroundingWhitespace() {
        assertThat(TransferId.of("\ttransfer-1\n").value()).isEqualTo("transfer-1");
    }

    @Test
    void shouldTrimIdempotencyKeyWhenValueHasSurroundingWhitespace() {
        assertThat(IdempotencyKey.of("  key-1  ").value()).isEqualTo("key-1");
    }

    @Test
    void shouldTreatIdentifiersAsEqualWhenValuesMatchAfterTrimming() {
        assertThat(AccountId.of("  account-1  ")).isEqualTo(AccountId.of("account-1"))
                .hasSameHashCodeAs(AccountId.of("account-1"));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"客户-4711", "Ünïcödé", "владелец", "ключ-💰"})
    void shouldAcceptIdentifierWhenValueContainsUnicode(String value) {
        assertThat(AccountId.of(value).value()).isEqualTo(value);
    }

    @Test
    void shouldSeparateTheReservedFundingSourceWhenComparedWithOrdinaryIdentifiers() {
        assertThat(AccountId.externalFundingSource()).isNotEqualTo(AccountId.of("account-1"));
    }

}
