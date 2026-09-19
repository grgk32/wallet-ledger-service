package com.example.walletledger.core.service;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.EntryDirection;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferPostingTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final Instant POSTED_AT = Instant.parse("2026-03-04T10:15:30Z");
    private static final TransferId TRANSFER_ID = TransferId.of("transfer-1");

    @Test
    void shouldRetainBothAccountsWhenPostingIsCreated() {
        // given
        var source = accountWith("source", "75.00");
        var target = accountWith("target", "35.00");

        // when
        var posting = new TransferPosting(source, target, List.of(debitEntry()));

        // then
        assertThat(posting).extracting(TransferPosting::debitedSource, TransferPosting::creditedTarget)
                .containsExactly(source, target);
    }

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingComponents")
    void shouldRejectPostingWhenMandatoryComponentIsNull(String componentName, ThrowingCallable construction) {
        // then
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(componentName);
    }

    @Test
    void shouldCopyTheEntriesWhenPostingIsCreated() {
        // given
        var mutableEntries = new ArrayList<LedgerEntry>();
        mutableEntries.add(debitEntry());
        var posting = new TransferPosting(accountWith("source", "75.00"), accountWith("target", "35.00"),
                mutableEntries);

        // when
        mutableEntries.clear();

        // then
        assertThat(posting.entries()).hasSize(1);
    }

    @Test
    void shouldExposeAnImmutableEntryListWhenPostingIsCreated() {
        // given
        var posting = new TransferPosting(accountWith("source", "75.00"), accountWith("target", "35.00"),
                List.of(debitEntry()));

        // then
        assertThatThrownBy(() -> posting.entries().add(debitEntry()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAcceptAnEmptyEntryListWhenPostingCarriesNoJournalLine() {
        // when
        var posting = new TransferPosting(accountWith("source", "75.00"), accountWith("target", "35.00"), List.of());

        // then
        assertThat(posting.entries()).isEmpty();
    }

    private static Stream<Arguments> missingComponents() {
        return Stream.of(
                Arguments.of("debitedSource", (ThrowingCallable) () ->
                        new TransferPosting(null, accountWith("target", "35.00"), List.of(debitEntry()))),
                Arguments.of("creditedTarget", (ThrowingCallable) () ->
                        new TransferPosting(accountWith("source", "75.00"), null, List.of(debitEntry()))),
                Arguments.of("entries", (ThrowingCallable) () ->
                        new TransferPosting(accountWith("source", "75.00"), accountWith("target", "35.00"), null)));
    }

    private static LedgerEntry debitEntry() {
        return new LedgerEntry("transfer-1:debit", TRANSFER_ID, AccountId.of("source"), EntryDirection.DEBIT,
                euros("25.00"), POSTED_AT);
    }

    private static Account accountWith(String accountId, String balance) {
        return new Account(AccountId.of(accountId), "owner-" + accountId, euros(balance), POSTED_AT);
    }

    private static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }
}
