package com.example.walletledger.usecases.service;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.usecases.api.CreateAccountCommand;
import com.example.walletledger.usecases.spi.AccountIdGenerator;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.LedgerWorkspace;
import com.example.walletledger.usecases.spi.TransferIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CreateAccountServiceTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final AccountId GENERATED_ACCOUNT_ID = AccountId.of("account-1");
    private static final TransferId GENERATED_TRANSFER_ID = TransferId.of("transfer-1");
    private static final Instant NOW = Instant.parse("2026-05-05T12:00:00Z");

    @Mock
    private LedgerTransactionSpiPort ledgerTransactions;

    @Mock
    private AccountIdGenerator accountIdGenerator;

    @Mock
    private TransferIdGenerator transferIdGenerator;

    @Mock
    private ClockSpiPort clock;

    @Mock
    private LedgerWorkspace workspace;

    @Captor
    private ArgumentCaptor<Account> savedAccountCaptor;

    @Captor
    private ArgumentCaptor<List<LedgerEntry>> appendedEntriesCaptor;

    private CreateAccountService createAccountService;

    @BeforeEach
    void createService() {
        createAccountService = new CreateAccountService(ledgerTransactions, accountIdGenerator, transferIdGenerator,
                clock);
        given(accountIdGenerator.nextAccountId()).willReturn(GENERATED_ACCOUNT_ID);
        given(clock.now()).willReturn(NOW);
        givenWorkspaceIsExecuted();
    }

    @Test
    void shouldReturnSnapshotWhenAccountIsOpened() {
        given(transferIdGenerator.nextTransferId()).willReturn(GENERATED_TRANSFER_ID);

        var snapshot = createAccountService.createAccount(commandWith("100.00"));

        assertThat(snapshot).extracting(accountSnapshot -> accountSnapshot.accountId(),
                        accountSnapshot -> accountSnapshot.ownerReference(),
                        accountSnapshot -> accountSnapshot.balance(),
                        accountSnapshot -> accountSnapshot.observedAt())
                .containsExactly(GENERATED_ACCOUNT_ID, "customer-1", euros("100.00"), NOW);
    }

    @Test
    void shouldSaveAccountWithinTransactionWhenAccountIsOpened() {
        given(transferIdGenerator.nextTransferId()).willReturn(GENERATED_TRANSFER_ID);

        createAccountService.createAccount(commandWith("100.00"));

        then(workspace).should().save(savedAccountCaptor.capture());
        assertThat(savedAccountCaptor.getValue().accountId()).isEqualTo(GENERATED_ACCOUNT_ID);
    }

    @Test
    void shouldLockOnlyTheNewAccountWhenAccountIsOpened() {
        given(transferIdGenerator.nextTransferId()).willReturn(GENERATED_TRANSFER_ID);
        var participantsCaptor = ArgumentCaptor.<Set<AccountId>>captor();

        createAccountService.createAccount(commandWith("100.00"));

        then(ledgerTransactions).should().executeWithin(participantsCaptor.capture(), any());
        assertThat(participantsCaptor.getValue()).containsExactly(GENERATED_ACCOUNT_ID);
    }

    @Test
    void shouldAppendBalancedFundingEntriesWhenOpeningBalanceIsPositive() {
        given(transferIdGenerator.nextTransferId()).willReturn(GENERATED_TRANSFER_ID);

        createAccountService.createAccount(commandWith("100.00"));

        then(workspace).should().appendEntries(appendedEntriesCaptor.capture());
        assertThat(appendedEntriesCaptor.getValue())
                .hasSize(2)
                .extracting(LedgerEntry::accountId)
                .containsExactly(AccountId.externalFundingSource(), GENERATED_ACCOUNT_ID);
    }

    @Test
    void shouldAppendNoEntriesWhenOpeningBalanceIsZero() {
        createAccountService.createAccount(commandWith("0.00"));

        then(workspace).should(never()).appendEntries(any());
    }

    @Test
    void shouldStampTheOpeningInstantWhenAccountIsOpened() {
        createAccountService.createAccount(commandWith("0.00"));

        then(workspace).should().save(savedAccountCaptor.capture());
        assertThat(savedAccountCaptor.getValue().openedAt()).isEqualTo(NOW);
    }

    private void givenWorkspaceIsExecuted() {
        given(ledgerTransactions.executeWithin(any(), any())).willAnswer(invocation -> {
            Function<LedgerWorkspace, Object> operation = invocation.getArgument(1);
            return operation.apply(workspace);
        });
    }

    private static CreateAccountCommand commandWith(String initialBalance) {
        return new CreateAccountCommand("customer-1", EURO, new BigDecimal(initialBalance));
    }

    private static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }
}
