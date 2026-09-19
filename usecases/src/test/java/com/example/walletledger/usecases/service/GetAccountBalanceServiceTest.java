package com.example.walletledger.usecases.service;

import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.usecases.exception.AccountNotFoundException;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.LedgerWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetAccountBalanceServiceTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final AccountId ACCOUNT_ID = AccountId.of("account-1");
    private static final Instant OPENED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-07T07:07:07Z");

    @Mock
    private LedgerTransactionSpiPort ledgerTransactions;

    @Mock
    private ClockSpiPort clock;

    @Mock
    private LedgerWorkspace workspace;

    private GetAccountBalanceService getAccountBalanceService;

    @BeforeEach
    void createService() {
        getAccountBalanceService = new GetAccountBalanceService(ledgerTransactions, clock);
        given(ledgerTransactions.executeWithin(any(), any())).willAnswer(invocation -> {
            Function<LedgerWorkspace, Object> operation = invocation.getArgument(1);
            return operation.apply(workspace);
        });
    }

    @Test
    void shouldReturnBalanceWhenAccountExists() {
        given(workspace.findAccount(ACCOUNT_ID)).willReturn(Optional.of(
                new Account(ACCOUNT_ID, "customer-1", euros("42.00"), OPENED_AT)));
        given(clock.now()).willReturn(OBSERVED_AT);

        var snapshot = getAccountBalanceService.getBalance(ACCOUNT_ID);

        assertThat(snapshot.balance()).isEqualTo(euros("42.00"));
    }

    @Test
    void shouldStampTheObservationInstantWhenAccountExists() {
        given(workspace.findAccount(ACCOUNT_ID)).willReturn(Optional.of(
                new Account(ACCOUNT_ID, "customer-1", euros("42.00"), OPENED_AT)));
        given(clock.now()).willReturn(OBSERVED_AT);

        var snapshot = getAccountBalanceService.getBalance(ACCOUNT_ID);

        assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void shouldRaiseNotFoundWhenAccountIsAbsent() {
        given(workspace.findAccount(ACCOUNT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> getAccountBalanceService.getBalance(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(ACCOUNT_ID.value());
    }

    private static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }
}
