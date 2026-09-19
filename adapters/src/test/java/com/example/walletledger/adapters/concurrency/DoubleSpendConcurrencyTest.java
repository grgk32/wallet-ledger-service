package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.RejectionReason;
import com.example.walletledger.usecases.exception.TransferRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleSpendConcurrencyTest {

    private static final int COMPETING_CLIENTS = 100;
    private static final int AFFORDABLE_TRANSFERS = 10;
    private static final String OPENING_BALANCE = "100.00";
    private static final String TRANSFER_AMOUNT = "10.00";

    private LedgerTestFixture ledger;
    private AccountId sourceAccountId;
    private AccountId targetAccountId;
    private ExecutorService clients;

    @BeforeEach
    void openAccounts() {
        ledger = new LedgerTestFixture();
        sourceAccountId = ledger.openAccount(OPENING_BALANCE);
        targetAccountId = ledger.openAccount("0.00");
        clients = Executors.newFixedThreadPool(COMPETING_CLIENTS);
    }

    @AfterEach
    void stopClients() {
        clients.shutdownNow();
    }

    @Test
    void shouldReleaseExactlyTenTransfersWhenHundredConcurrentDebitsCompeteForHundredUnits() throws Exception {
        // when
        var outcomes = runCompetingDebits();

        // then
        assertThat(outcomes).filteredOn(TransferAttempt::applied).hasSize(AFFORDABLE_TRANSFERS);
    }

    @Test
    void shouldRejectEveryUnaffordableTransferWhenHundredConcurrentDebitsCompeteForHundredUnits() throws Exception {
        // when
        var outcomes = runCompetingDebits();

        // then
        assertThat(outcomes).filteredOn(attempt -> !attempt.applied())
                .hasSize(COMPETING_CLIENTS - AFFORDABLE_TRANSFERS)
                .allSatisfy(attempt -> assertThat(attempt.reason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS));
    }

    @Test
    void shouldDrainTheSourceAccountWhenHundredConcurrentDebitsCompeteForHundredUnits() throws Exception {
        // when
        runCompetingDebits();

        // then
        assertThat(ledger.balanceOf(sourceAccountId)).isEqualTo(LedgerTestFixture.euros("0.00"));
    }

    @Test
    void shouldCreditOnlyTheAffordableAmountWhenHundredConcurrentDebitsCompeteForHundredUnits() throws Exception {
        // when
        runCompetingDebits();

        // then
        assertThat(ledger.balanceOf(targetAccountId)).isEqualTo(LedgerTestFixture.euros("100.00"));
    }

    private List<TransferAttempt> runCompetingDebits() throws Exception {
        var startGate = new CyclicBarrier(COMPETING_CLIENTS);
        var attempts = new ArrayList<Future<TransferAttempt>>(COMPETING_CLIENTS);
        for (var client = 0; client < COMPETING_CLIENTS; client++) {
            var idempotencyKey = "double-spend-" + client;
            attempts.add(clients.submit(() -> {
                awaitStartGate(startGate);
                return attemptTransfer(idempotencyKey);
            }));
        }
        var outcomes = new ArrayList<TransferAttempt>(COMPETING_CLIENTS);
        for (var attempt : attempts) {
            outcomes.add(attempt.get(60, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    private TransferAttempt attemptTransfer(String idempotencyKey) {
        try {
            ledger.transfer(sourceAccountId, targetAccountId, TRANSFER_AMOUNT, idempotencyKey);
            return new TransferAttempt(true, null);
        } catch (TransferRejectedException rejection) {
            return new TransferAttempt(false, rejection.reason());
        }
    }

    private static void awaitStartGate(CyclicBarrier startGate) {
        try {
            startGate.await(60, TimeUnit.SECONDS);
        } catch (Exception startGateFailure) {
            throw new IllegalStateException("the start gate was not reached", startGateFailure);
        }
    }

    private record TransferAttempt(boolean applied, RejectionReason reason) {
    }
}
