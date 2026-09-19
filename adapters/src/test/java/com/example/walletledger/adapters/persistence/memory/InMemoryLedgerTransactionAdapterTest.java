package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.EntryDirection;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryLedgerTransactionAdapterTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final AccountId PARTICIPANT = AccountId.of("participant");
    private static final AccountId SECOND_PARTICIPANT = AccountId.of("second-participant");
    private static final AccountId OUTSIDER = AccountId.of("outsider");
    private static final TransferId TRANSFER_ID = TransferId.of("transfer-1");
    private static final Instant POSTED_AT = Instant.parse("2026-06-06T09:00:00Z");

    private InMemoryLedgerStore store;
    private InMemoryLedgerTransactionAdapter ledgerTransactions;

    @BeforeEach
    void createAdapter() {
        store = new InMemoryLedgerStore();
        ledgerTransactions = new InMemoryLedgerTransactionAdapter(store, new AccountLockRegistry());
    }

    @Test
    void shouldCommitStagedAccountsWhenOperationCompletes() {
        // given
        var participants = Set.of(PARTICIPANT, SECOND_PARTICIPANT);

        // when
        ledgerTransactions.executeWithin(participants, workspace -> {
            workspace.save(accountWith(PARTICIPANT, "100.00"));
            workspace.save(accountWith(SECOND_PARTICIPANT, "50.00"));
            return null;
        });

        // then
        assertThat(store.findAccount(PARTICIPANT)).hasValueSatisfying(
                account -> assertThat(account.balance()).isEqualTo(euros("100.00")));
    }

    @Test
    void shouldCommitStagedEntriesWhenOperationCompletes() {
        // when
        ledgerTransactions.executeWithin(Set.of(PARTICIPANT), workspace -> {
            workspace.save(accountWith(PARTICIPANT, "100.00"));
            workspace.appendEntries(List.of(entryFor(PARTICIPANT, EntryDirection.CREDIT, "100.00")));
            return null;
        });

        // then
        assertThat(store.entriesOf(PARTICIPANT)).hasSize(1);
    }

    @Test
    void shouldCommitNothingWhenOperationFails() {
        // given
        store.putAccount(accountWith(PARTICIPANT, "100.00"));

        // when
        assertThatThrownBy(() -> ledgerTransactions.executeWithin(Set.of(PARTICIPANT), workspace -> {
            workspace.save(accountWith(PARTICIPANT, "0.00"));
            workspace.appendEntries(List.of(entryFor(PARTICIPANT, EntryDirection.DEBIT, "100.00")));
            throw new IllegalStateException("posting failed");
        })).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(store.findAccount(PARTICIPANT)).hasValueSatisfying(
                account -> assertThat(account.balance()).isEqualTo(euros("100.00")));
    }

    @Test
    void shouldAppendNoEntriesWhenOperationFails() {
        // when
        assertThatThrownBy(() -> ledgerTransactions.executeWithin(Set.of(PARTICIPANT), workspace -> {
            workspace.appendEntries(List.of(entryFor(PARTICIPANT, EntryDirection.DEBIT, "100.00")));
            throw new IllegalStateException("posting failed");
        })).isInstanceOf(IllegalStateException.class);

        // then
        assertThat(store.entriesOf(PARTICIPANT)).isEmpty();
    }

    @Test
    void shouldObserveStagedValueWhenAccountIsReadAfterWrite() {
        // when
        var stagedBalance = ledgerTransactions.executeWithin(Set.of(PARTICIPANT), workspace -> {
            workspace.save(accountWith(PARTICIPANT, "42.00"));
            return workspace.findAccount(PARTICIPANT).orElseThrow().balance();
        });

        // then
        assertThat(stagedBalance).isEqualTo(euros("42.00"));
    }

    @Test
    void shouldRejectReadWhenAccountIsNotAParticipant() {
        // when
        assertThatThrownBy(() -> ledgerTransactions.executeWithin(Set.of(PARTICIPANT),
                workspace -> workspace.findAccount(OUTSIDER)))
                // then
                .isInstanceOf(UnlockedAccountAccessException.class)
                .hasMessageContaining(OUTSIDER.value());
    }

    @Test
    void shouldRejectWriteWhenAccountIsNotAParticipant() {
        // when
        assertThatThrownBy(() -> ledgerTransactions.executeWithin(Set.of(PARTICIPANT), workspace -> {
            workspace.save(accountWith(OUTSIDER, "1.00"));
            return null;
        }))
                // then
                .isInstanceOf(UnlockedAccountAccessException.class);
    }

    @Test
    void shouldReturnEmptyAccountWhenParticipantWasNeverOpened() {
        // when
        var absentAccount = ledgerTransactions.executeWithin(Set.of(PARTICIPANT),
                workspace -> workspace.findAccount(PARTICIPANT));

        // then
        assertThat(absentAccount).isEmpty();
    }

    @Test
    void shouldReturnOperationResultWhenOperationCompletes() {
        // when
        var result = ledgerTransactions.executeWithin(Set.of(PARTICIPANT), workspace -> "committed");

        // then
        assertThat(result).isEqualTo("committed");
    }

    @Test
    void shouldReleaseParticipantLocksWhenOperationFails() {
        // given
        assertThatThrownBy(() -> ledgerTransactions.executeWithin(Set.of(PARTICIPANT), workspace -> {
            throw new IllegalStateException("posting failed");
        })).isInstanceOf(IllegalStateException.class);

        // when
        var secondAttempt = ledgerTransactions.executeWithin(Set.of(PARTICIPANT), workspace -> "committed");

        // then
        assertThat(secondAttempt).isEqualTo("committed");
    }

    private static Account accountWith(AccountId accountId, String balance) {
        return new Account(accountId, "owner-" + accountId.value(), euros(balance), POSTED_AT);
    }

    private static LedgerEntry entryFor(AccountId accountId, EntryDirection direction, String amount) {
        return new LedgerEntry(TRANSFER_ID.value() + ":" + direction.name(), TRANSFER_ID, accountId, direction,
                euros(amount), POSTED_AT);
    }

    private static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }
}
