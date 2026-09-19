package com.example.walletledger.core.model;

import com.example.walletledger.core.exception.InvalidIdentifierException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerEntryTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final String ENTRY_ID = "transfer-1:debit";
    private static final TransferId TRANSFER_ID = TransferId.of("transfer-1");
    private static final AccountId ACCOUNT_ID = AccountId.of("account-1");
    private static final Money AMOUNT = Money.of(new BigDecimal("25.00"), EURO);
    private static final Instant POSTED_AT = Instant.parse("2026-03-04T10:15:30Z");

    @Test
    void shouldRetainEveryComponentWhenEntryIsCreated() {
        // when
        var entry = debitEntry();

        // then
        assertThat(entry).extracting(LedgerEntry::entryId, LedgerEntry::transferId, LedgerEntry::accountId,
                        LedgerEntry::direction, LedgerEntry::amount, LedgerEntry::postedAt)
                .containsExactly(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, EntryDirection.DEBIT, AMOUNT, POSTED_AT);
    }

    @ParameterizedTest(name = "{index}: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void shouldRejectEntryWhenEntryIdentifierIsBlank(String entryId) {
        // then
        assertThatThrownBy(() -> new LedgerEntry(entryId, TRANSFER_ID, ACCOUNT_ID, EntryDirection.DEBIT, AMOUNT,
                POSTED_AT)).isInstanceOf(InvalidIdentifierException.class);
    }

    @Test
    void shouldRejectEntryWhenEntryIdentifierExceedsMaximumLength() {
        // given
        var tooLong = "e".repeat(161);

        // then
        assertThatThrownBy(() -> new LedgerEntry(tooLong, TRANSFER_ID, ACCOUNT_ID, EntryDirection.DEBIT, AMOUNT,
                POSTED_AT))
                .isInstanceOf(InvalidIdentifierException.class)
                .hasMessageContaining("160");
    }

    @Test
    void shouldAcceptEntryWhenEntryIdentifierLengthIsAtTheMaximum() {
        // given
        var atLimit = "e".repeat(160);

        // when
        var entry = new LedgerEntry(atLimit, TRANSFER_ID, ACCOUNT_ID, EntryDirection.DEBIT, AMOUNT, POSTED_AT);

        // then
        assertThat(entry.entryId()).hasSize(160);
    }

    @Test
    void shouldTrimEntryIdentifierWhenValueHasSurroundingWhitespace() {
        // when
        var entry = new LedgerEntry("  " + ENTRY_ID + "  ", TRANSFER_ID, ACCOUNT_ID, EntryDirection.DEBIT, AMOUNT,
                POSTED_AT);

        // then
        assertThat(entry.entryId()).isEqualTo(ENTRY_ID);
    }

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingComponents")
    void shouldRejectEntryWhenMandatoryComponentIsNull(String componentName, ThrowingCallable construction) {
        // then
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(componentName);
    }

    @ParameterizedTest(name = "{index}: {0} -> debit={1} credit={2}")
    @CsvSource({"DEBIT,true,false", "CREDIT,false,true"})
    void shouldClassifyTheEntryWhenDirectionIsInspected(EntryDirection direction,
                                                        boolean expectedDebit,
                                                        boolean expectedCredit) {
        // when
        var entry = new LedgerEntry(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, direction, AMOUNT, POSTED_AT);

        // then
        assertThat(entry).extracting(LedgerEntry::isDebit, LedgerEntry::isCredit)
                .containsExactly(expectedDebit, expectedCredit);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(EntryDirection.class)
    void shouldCarryExactlyOneDirectionWhenEntryIsPosted(EntryDirection direction) {
        // when
        var entry = new LedgerEntry(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, direction, AMOUNT, POSTED_AT);

        // then
        assertThat(entry.isDebit() ^ entry.isCredit()).isTrue();
    }

    @Test
    void shouldAcceptEntryWhenAmountIsZero() {
        // when
        var entry = new LedgerEntry(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, EntryDirection.CREDIT, Money.zero(EURO),
                POSTED_AT);

        // then
        assertThat(entry.amount().isZero()).isTrue();
    }

    @Test
    void shouldTreatEntriesAsEqualWhenEveryComponentMatches() {
        // then
        assertThat(debitEntry()).isEqualTo(debitEntry()).hasSameHashCodeAs(debitEntry());
    }

    @Test
    void shouldTreatEntriesAsDifferentWhenDirectionDiffers() {
        // given
        var credit = new LedgerEntry(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, EntryDirection.CREDIT, AMOUNT, POSTED_AT);

        // then
        assertThat(debitEntry()).isNotEqualTo(credit);
    }

    private static Stream<Arguments> missingComponents() {
        return Stream.of(
                Arguments.of("transferId", (ThrowingCallable) () ->
                        new LedgerEntry(ENTRY_ID, null, ACCOUNT_ID, EntryDirection.DEBIT, AMOUNT, POSTED_AT)),
                Arguments.of("accountId", (ThrowingCallable) () ->
                        new LedgerEntry(ENTRY_ID, TRANSFER_ID, null, EntryDirection.DEBIT, AMOUNT, POSTED_AT)),
                Arguments.of("direction", (ThrowingCallable) () ->
                        new LedgerEntry(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, null, AMOUNT, POSTED_AT)),
                Arguments.of("amount", (ThrowingCallable) () ->
                        new LedgerEntry(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, EntryDirection.DEBIT, null, POSTED_AT)),
                Arguments.of("postedAt", (ThrowingCallable) () ->
                        new LedgerEntry(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, EntryDirection.DEBIT, AMOUNT, null)));
    }

    private static LedgerEntry debitEntry() {
        return new LedgerEntry(ENTRY_ID, TRANSFER_ID, ACCOUNT_ID, EntryDirection.DEBIT, AMOUNT, POSTED_AT);
    }
}
