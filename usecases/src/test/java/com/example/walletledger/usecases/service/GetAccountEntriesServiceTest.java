package com.example.walletledger.usecases.service;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.EntryDirection;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.usecases.exception.AccountNotFoundException;
import com.example.walletledger.usecases.spi.AccountSpiPort;
import com.example.walletledger.usecases.spi.LedgerJournalSpiPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetAccountEntriesServiceTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final AccountId ACCOUNT_ID = AccountId.of("account-1");
    private static final Instant POSTED_AT = Instant.parse("2026-08-08T08:08:08Z");

    @Mock
    private AccountSpiPort accounts;

    @Mock
    private LedgerJournalSpiPort ledgerJournal;

    private GetAccountEntriesService getAccountEntriesService;

    @BeforeEach
    void createService() {
        getAccountEntriesService = new GetAccountEntriesService(accounts, ledgerJournal);
    }

    @Test
    void shouldReturnJournalWhenAccountExists() {
        given(accounts.existsById(ACCOUNT_ID)).willReturn(true);
        given(ledgerJournal.findByAccount(ACCOUNT_ID)).willReturn(List.of(entry()));

        var entries = getAccountEntriesService.findEntries(ACCOUNT_ID);

        assertThat(entries).hasSize(1).first().isEqualTo(entry());
    }

    @Test
    void shouldReturnEmptyListWhenJournalIsEmpty() {
        given(accounts.existsById(ACCOUNT_ID)).willReturn(true);
        given(ledgerJournal.findByAccount(ACCOUNT_ID)).willReturn(List.of());

        var entries = getAccountEntriesService.findEntries(ACCOUNT_ID);

        assertThat(entries).isEmpty();
    }

    @Test
    void shouldRaiseNotFoundWhenAccountIsAbsent() {
        given(accounts.existsById(ACCOUNT_ID)).willReturn(false);

        assertThatThrownBy(() -> getAccountEntriesService.findEntries(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private static LedgerEntry entry() {
        return new LedgerEntry("transfer-1:debit", TransferId.of("transfer-1"), ACCOUNT_ID, EntryDirection.DEBIT,
                Money.of(new BigDecimal("5.00"), EURO), POSTED_AT);
    }
}
