package com.example.walletledger.core.service;

import com.example.walletledger.core.exception.CurrencyMismatchException;
import com.example.walletledger.core.exception.InsufficientFundsException;
import com.example.walletledger.core.exception.InvalidMonetaryAmountException;
import com.example.walletledger.core.exception.SameAccountTransferException;
import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.EntryDirection;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerPostingTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final Currency DOLLAR = Currency.getInstance("USD");
    private static final Instant POSTED_AT = Instant.parse("2026-03-04T10:15:30Z");
    private static final TransferId TRANSFER_ID = TransferId.of("transfer-1");

    @Test
    void shouldDebitSourceAndCreditTargetWhenPostingIsBalanced() {
        var posting = LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "10.00"),
                euros("25.00"), TRANSFER_ID, POSTED_AT);

        assertThat(posting).extracting(transferPosting -> transferPosting.debitedSource().balance(),
                        transferPosting -> transferPosting.creditedTarget().balance())
                .containsExactly(euros("75.00"), euros("35.00"));
    }

    @Test
    void shouldEmitOneDebitAndOneCreditWhenPostingIsBalanced() {
        var posting = LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "10.00"),
                euros("25.00"), TRANSFER_ID, POSTED_AT);

        assertThat(posting.entries())
                .hasSize(2)
                .extracting(LedgerEntry::accountId, LedgerEntry::direction, LedgerEntry::amount)
                .containsExactly(
                        Tuple.tuple(AccountId.of("source"), EntryDirection.DEBIT,
                                euros("25.00")),
                        Tuple.tuple(AccountId.of("target"), EntryDirection.CREDIT,
                                euros("25.00")));
    }

    @Test
    void shouldDeriveEntryIdentifiersWhenPostingIsBalanced() {
        var posting = LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "10.00"),
                euros("25.00"), TRANSFER_ID, POSTED_AT);

        assertThat(posting.entries()).extracting(LedgerEntry::entryId)
                .containsExactly("transfer-1:debit", "transfer-1:credit");
    }

    @Test
    void shouldShareOneTransferIdentifierWhenPostingIsBalanced() {
        var posting = LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "10.00"),
                euros("25.00"), TRANSFER_ID, POSTED_AT);

        assertThat(posting.entries()).allSatisfy(entry -> assertThat(entry.transferId()).isEqualTo(TRANSFER_ID));
    }

    @Test
    void shouldRejectPostingWhenSourceAndTargetAreTheSameAccount() {
        var account = accountWith("same", "100.00");

        assertThatThrownBy(() -> LedgerPosting.post(account, account, euros("1.00"), TRANSFER_ID, POSTED_AT))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void shouldRejectPostingWhenSourceCannotCoverTheAmount() {
        assertThatThrownBy(() -> LedgerPosting.post(accountWith("source", "1.00"), accountWith("target", "0.00"),
                euros("1.01"), TRANSFER_ID, POSTED_AT))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void shouldDebitTheReservedFundingAccountWhenAccountIsFunded() {
        var entries = LedgerPosting.fund(accountWith("target", "100.00"), TRANSFER_ID, POSTED_AT);

        assertThat(entries).first()
                .satisfies(entry -> assertThat(entry.accountId()).isEqualTo(AccountId.externalFundingSource()))
                .satisfies(entry -> assertThat(entry.isDebit()).isTrue());
    }

    @Test
    void shouldCreditTheOpeningBalanceWhenAccountIsFunded() {
        var entries = LedgerPosting.fund(accountWith("target", "100.00"), TRANSFER_ID, POSTED_AT);

        assertThat(entries).last()
                .satisfies(entry -> assertThat(entry.accountId()).isEqualTo(AccountId.of("target")))
                .satisfies(entry -> assertThat(entry.isCredit()).isTrue())
                .satisfies(entry -> assertThat(entry.amount()).isEqualTo(euros("100.00")));
    }

    @Test
    void shouldRejectPostingWhenSourceAndTargetHoldDifferentCurrencies() {
        var source = accountWith("source", "100.00");
        var target = new Account(AccountId.of("target"), "owner-target",
                Money.of(new BigDecimal("10.00"), DOLLAR), POSTED_AT);

        assertThatThrownBy(() -> LedgerPosting.post(source, target, euros("25.00"), TRANSFER_ID, POSTED_AT))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void shouldRejectPostingWhenAmountIsZero() {
        assertThatThrownBy(() -> LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "0.00"),
                Money.zero(EURO), TRANSFER_ID, POSTED_AT))
                .isInstanceOf(InvalidMonetaryAmountException.class)
                .hasMessageContaining("strictly positive");
    }

    @Test
    void shouldRejectPostingWhenAmountIsNull() {
        assertThatThrownBy(() -> LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "0.00"),
                null, TRANSFER_ID, POSTED_AT))
                .isInstanceOf(InvalidMonetaryAmountException.class);
    }

    @Test
    void shouldLeaveTheSuppliedAccountsUnchangedWhenPostingSucceeds() {
        var source = accountWith("source", "100.00");
        var target = accountWith("target", "10.00");

        LedgerPosting.post(source, target, euros("25.00"), TRANSFER_ID, POSTED_AT);

        assertThat(source.balance()).isEqualTo(euros("100.00"));
        assertThat(target.balance()).isEqualTo(euros("10.00"));
    }

    @Test
    void shouldConserveMoneyWhenPostingIsBalanced() {
        var posting = LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "10.00"),
                euros("25.00"), TRANSFER_ID, POSTED_AT);

        var total = posting.debitedSource().balance().add(posting.creditedTarget().balance());

        assertThat(total).isEqualTo(euros("110.00"));
    }

    @Test
    void shouldStampEveryEntryWithThePostingInstantWhenPostingIsBalanced() {
        var posting = LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "10.00"),
                euros("25.00"), TRANSFER_ID, POSTED_AT);

        assertThat(posting.entries()).allSatisfy(entry -> assertThat(entry.postedAt()).isEqualTo(POSTED_AT));
    }

    @Test
    void shouldExposeAnImmutableEntryListWhenPostingIsBalanced() {
        var posting = LedgerPosting.post(accountWith("source", "100.00"), accountWith("target", "10.00"),
                euros("25.00"), TRANSFER_ID, POSTED_AT);

        assertThatThrownBy(() -> posting.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldEmitOneDebitAndOneCreditWhenAccountIsFunded() {
        var entries = LedgerPosting.fund(accountWith("target", "100.00"), TRANSFER_ID, POSTED_AT);

        assertThat(entries).hasSize(2)
                .extracting(LedgerEntry::direction)
                .containsExactly(EntryDirection.DEBIT, EntryDirection.CREDIT);
    }

    @Test
    void shouldDeriveEntryIdentifiersWhenAccountIsFunded() {
        var entries = LedgerPosting.fund(accountWith("target", "100.00"), TRANSFER_ID, POSTED_AT);

        assertThat(entries).extracting(LedgerEntry::entryId).containsExactly("transfer-1:debit", "transfer-1:credit");
    }

    @Test
    void shouldStampEveryEntryWithTheOpeningInstantWhenAccountIsFunded() {
        var entries = LedgerPosting.fund(accountWith("target", "100.00"), TRANSFER_ID, POSTED_AT);

        assertThat(entries).allSatisfy(entry -> assertThat(entry.postedAt()).isEqualTo(POSTED_AT));
    }

    @Test
    void shouldShareOneTransferIdentifierWhenAccountIsFunded() {
        var entries = LedgerPosting.fund(accountWith("target", "100.00"), TRANSFER_ID, POSTED_AT);

        assertThat(entries).allSatisfy(entry -> assertThat(entry.transferId()).isEqualTo(TRANSFER_ID));
    }

    @Test
    void shouldRecordZeroAmountsWhenAccountIsOpenedWithoutFunds() {
        var entries = LedgerPosting.fund(accountWith("target", "0.00"), TRANSFER_ID, POSTED_AT);

        assertThat(entries).allSatisfy(entry -> assertThat(entry.amount().isZero()).isTrue());
    }

    private static Account accountWith(String accountId, String balance) {
        return new Account(AccountId.of(accountId), "owner-" + accountId, euros(balance), POSTED_AT);
    }

    private static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }
}
