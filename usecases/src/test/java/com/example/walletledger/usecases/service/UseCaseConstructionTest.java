package com.example.walletledger.usecases.service;

import com.example.walletledger.usecases.api.CreateAccountApiPort;
import com.example.walletledger.usecases.api.GetAccountBalanceApiPort;
import com.example.walletledger.usecases.api.GetAccountEntriesApiPort;
import com.example.walletledger.usecases.api.TransferMoneyApiPort;
import com.example.walletledger.usecases.spi.AccountIdGenerator;
import com.example.walletledger.usecases.spi.AccountSpiPort;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.IdempotencyRecordSpiPort;
import com.example.walletledger.usecases.spi.LedgerJournalSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.TransferIdGenerator;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UseCaseConstructionTest {

    private static final Duration WAIT_BUDGET = Duration.ofMillis(200);

    @ParameterizedTest(name = "{index}: {0} is mandatory")
    @MethodSource("missingCollaborators")
    void shouldRejectConstructionWhenCollaboratorIsMissing(String collaboratorName, ThrowingCallable construction) {
        // then
        assertThatThrownBy(construction)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(collaboratorName);
    }

    @Test
    void shouldImplementTheTransferPortWhenServiceIsConstructed() {
        // then
        assertThat(transferMoneyService()).isInstanceOf(TransferMoneyApiPort.class);
    }

    @Test
    void shouldImplementTheAccountCreationPortWhenServiceIsConstructed() {
        // then
        assertThat(createAccountService()).isInstanceOf(CreateAccountApiPort.class);
    }

    @Test
    void shouldImplementTheBalancePortWhenServiceIsConstructed() {
        // then
        assertThat(getAccountBalanceService()).isInstanceOf(GetAccountBalanceApiPort.class);
    }

    @Test
    void shouldImplementTheEntriesPortWhenServiceIsConstructed() {
        // then
        assertThat(getAccountEntriesService()).isInstanceOf(GetAccountEntriesApiPort.class);
    }

    private static Stream<Arguments> missingCollaborators() {
        return Stream.of(
                Arguments.of("ledgerTransactions", (ThrowingCallable) () -> new TransferMoneyService(null,
                        mock(IdempotencyRecordSpiPort.class), mock(TransferIdGenerator.class),
                        mock(ClockSpiPort.class), WAIT_BUDGET)),
                Arguments.of("idempotencyRecords", (ThrowingCallable) () -> new TransferMoneyService(
                        mock(LedgerTransactionSpiPort.class), null, mock(TransferIdGenerator.class),
                        mock(ClockSpiPort.class), WAIT_BUDGET)),
                Arguments.of("transferIdGenerator", (ThrowingCallable) () -> new TransferMoneyService(
                        mock(LedgerTransactionSpiPort.class), mock(IdempotencyRecordSpiPort.class), null,
                        mock(ClockSpiPort.class), WAIT_BUDGET)),
                Arguments.of("clock", (ThrowingCallable) () -> new TransferMoneyService(
                        mock(LedgerTransactionSpiPort.class), mock(IdempotencyRecordSpiPort.class),
                        mock(TransferIdGenerator.class), null, WAIT_BUDGET)),
                Arguments.of("inFlightWaitBudget", (ThrowingCallable) () -> new TransferMoneyService(
                        mock(LedgerTransactionSpiPort.class), mock(IdempotencyRecordSpiPort.class),
                        mock(TransferIdGenerator.class), mock(ClockSpiPort.class), null)),
                Arguments.of("ledgerTransactions", (ThrowingCallable) () -> new CreateAccountService(null,
                        mock(AccountIdGenerator.class), mock(TransferIdGenerator.class), mock(ClockSpiPort.class))),
                Arguments.of("accountIdGenerator", (ThrowingCallable) () -> new CreateAccountService(
                        mock(LedgerTransactionSpiPort.class), null, mock(TransferIdGenerator.class),
                        mock(ClockSpiPort.class))),
                Arguments.of("transferIdGenerator", (ThrowingCallable) () -> new CreateAccountService(
                        mock(LedgerTransactionSpiPort.class), mock(AccountIdGenerator.class), null,
                        mock(ClockSpiPort.class))),
                Arguments.of("clock", (ThrowingCallable) () -> new CreateAccountService(
                        mock(LedgerTransactionSpiPort.class), mock(AccountIdGenerator.class),
                        mock(TransferIdGenerator.class), null)),
                Arguments.of("ledgerTransactions", (ThrowingCallable) () ->
                        new GetAccountBalanceService(null, mock(ClockSpiPort.class))),
                Arguments.of("clock", (ThrowingCallable) () ->
                        new GetAccountBalanceService(mock(LedgerTransactionSpiPort.class), null)),
                Arguments.of("accounts", (ThrowingCallable) () ->
                        new GetAccountEntriesService(null, mock(LedgerJournalSpiPort.class))),
                Arguments.of("ledgerJournal", (ThrowingCallable) () ->
                        new GetAccountEntriesService(mock(AccountSpiPort.class), null)));
    }

    private static TransferMoneyService transferMoneyService() {
        return new TransferMoneyService(mock(LedgerTransactionSpiPort.class), mock(IdempotencyRecordSpiPort.class),
                mock(TransferIdGenerator.class), mock(ClockSpiPort.class), WAIT_BUDGET);
    }

    private static CreateAccountService createAccountService() {
        return new CreateAccountService(mock(LedgerTransactionSpiPort.class), mock(AccountIdGenerator.class),
                mock(TransferIdGenerator.class), mock(ClockSpiPort.class));
    }

    private static GetAccountBalanceService getAccountBalanceService() {
        return new GetAccountBalanceService(mock(LedgerTransactionSpiPort.class), mock(ClockSpiPort.class));
    }

    private static GetAccountEntriesService getAccountEntriesService() {
        return new GetAccountEntriesService(mock(AccountSpiPort.class), mock(LedgerJournalSpiPort.class));
    }
}
