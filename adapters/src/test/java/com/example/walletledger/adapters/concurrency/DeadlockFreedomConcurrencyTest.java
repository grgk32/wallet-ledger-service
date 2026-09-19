package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DeadlockFreedomConcurrencyTest {

    private static final int THREADS_PER_DIRECTION = 8;
    private static final int TRANSFERS_PER_THREAD = 250;
    private static final String OPENING_BALANCE = "100000.00";
    private static final String TRANSFER_AMOUNT = "1.00";
    private static final Duration DEADLOCK_BUDGET = Duration.ofSeconds(60);

    private LedgerTestFixture ledger;
    private AccountId firstAccountId;
    private AccountId secondAccountId;
    private ExecutorService clients;

    @BeforeEach
    void openAccounts() {
        ledger = new LedgerTestFixture();
        firstAccountId = ledger.openAccount(OPENING_BALANCE);
        secondAccountId = ledger.openAccount(OPENING_BALANCE);
        clients = Executors.newFixedThreadPool(2 * THREADS_PER_DIRECTION);
    }

    @AfterEach
    void stopClients() {
        clients.shutdownNow();
    }

    @Test
    void shouldCompleteAllTransfersWhenOpposingDirectionsRunConcurrently() {
        // when
        assertTimeoutPreemptively(DEADLOCK_BUDGET, this::runOpposingTransfers);

        // then
        assertThat(ledger.totalBalance()).isEqualTo(LedgerTestFixture.euros("200000.00"));
    }

    @Test
    void shouldLeaveBalancesUnchangedWhenOpposingDirectionsRunEqually() {
        // when
        assertTimeoutPreemptively(DEADLOCK_BUDGET, this::runOpposingTransfers);

        // then
        assertThat(ledger.balanceOf(firstAccountId)).isEqualTo(LedgerTestFixture.euros(OPENING_BALANCE));
    }

    @Test
    void shouldPostEveryEntryWhenOpposingDirectionsRunConcurrently() {
        // when
        assertTimeoutPreemptively(DEADLOCK_BUDGET, this::runOpposingTransfers);

        // then
        assertThat(ledger.entriesOf(firstAccountId))
                .hasSize(1 + 2 * THREADS_PER_DIRECTION * TRANSFERS_PER_THREAD);
    }

    private void runOpposingTransfers() throws Exception {
        var startGate = new CyclicBarrier(2 * THREADS_PER_DIRECTION);
        var runs = new ArrayList<Future<?>>(2 * THREADS_PER_DIRECTION);
        for (var threadIndex = 0; threadIndex < THREADS_PER_DIRECTION; threadIndex++) {
            runs.add(submitDirection(startGate, firstAccountId, secondAccountId, "forward-" + threadIndex));
            runs.add(submitDirection(startGate, secondAccountId, firstAccountId, "backward-" + threadIndex));
        }
        for (var run : runs) {
            run.get(DEADLOCK_BUDGET.toSeconds(), TimeUnit.SECONDS);
        }
    }

    private Future<?> submitDirection(CyclicBarrier startGate, AccountId sourceAccountId, AccountId targetAccountId,
                                      String keyPrefix) {
        return clients.submit(() -> {
            awaitStartGate(startGate);
            for (var transfer = 0; transfer < TRANSFERS_PER_THREAD; transfer++) {
                ledger.transfer(sourceAccountId, targetAccountId, TRANSFER_AMOUNT, keyPrefix + "-" + transfer);
            }
            return null;
        });
    }

    private static void awaitStartGate(CyclicBarrier startGate) {
        try {
            startGate.await(60, TimeUnit.SECONDS);
        } catch (Exception startGateFailure) {
            throw new IllegalStateException("the start gate was not reached", startGateFailure);
        }
    }
}
