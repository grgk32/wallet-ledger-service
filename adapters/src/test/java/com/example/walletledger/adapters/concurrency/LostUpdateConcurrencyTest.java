package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
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

class LostUpdateConcurrencyTest {

    private static final int SOURCE_COUNT = 50;
    private static final int CREDITS_PER_SOURCE = 20;
    private static final String SOURCE_OPENING_BALANCE = "100.00";
    private static final String CREDIT_AMOUNT = "1.00";

    private LedgerTestFixture ledger;
    private List<AccountId> sourceAccountIds;
    private AccountId targetAccountId;
    private ExecutorService clients;

    @BeforeEach
    void openAccounts() {
        ledger = new LedgerTestFixture();
        sourceAccountIds = new ArrayList<>(SOURCE_COUNT);
        for (var index = 0; index < SOURCE_COUNT; index++) {
            sourceAccountIds.add(ledger.openAccount(SOURCE_OPENING_BALANCE));
        }
        targetAccountId = ledger.openAccount("0.00");
        clients = Executors.newFixedThreadPool(SOURCE_COUNT);
    }

    @AfterEach
    void stopClients() {
        clients.shutdownNow();
    }

    @Test
    void shouldSumAllCreditsWhenManySourcesCreditOneAccountConcurrently() throws Exception {
        // when
        runConcurrentCredits();

        // then
        assertThat(ledger.balanceOf(targetAccountId)).isEqualTo(LedgerTestFixture.euros("1000.00"));
    }

    @Test
    void shouldDebitEverySourceWhenManySourcesCreditOneAccountConcurrently() throws Exception {
        // when
        runConcurrentCredits();

        // then
        assertThat(sourceAccountIds).allSatisfy(
                accountId -> assertThat(ledger.balanceOf(accountId)).isEqualTo(LedgerTestFixture.euros("80.00")));
    }

    @Test
    void shouldRecordEveryCreditEntryWhenManySourcesCreditOneAccountConcurrently() throws Exception {
        // when
        runConcurrentCredits();

        // then
        assertThat(ledger.entriesOf(targetAccountId)).hasSize(SOURCE_COUNT * CREDITS_PER_SOURCE);
    }

    private void runConcurrentCredits() throws Exception {
        var startGate = new CyclicBarrier(SOURCE_COUNT);
        var runs = new ArrayList<Future<?>>(SOURCE_COUNT);
        for (var index = 0; index < SOURCE_COUNT; index++) {
            var sourceIndex = index;
            runs.add(clients.submit(() -> {
                awaitStartGate(startGate);
                creditTargetFrom(sourceIndex);
                return null;
            }));
        }
        for (var run : runs) {
            run.get(60, TimeUnit.SECONDS);
        }
    }

    private void creditTargetFrom(int sourceIndex) {
        for (var credit = 0; credit < CREDITS_PER_SOURCE; credit++) {
            ledger.transfer(sourceAccountIds.get(sourceIndex), targetAccountId, CREDIT_AMOUNT,
                    "lost-update-" + sourceIndex + "-" + credit);
        }
    }

    private static void awaitStartGate(CyclicBarrier startGate) {
        try {
            startGate.await(60, TimeUnit.SECONDS);
        } catch (Exception startGateFailure) {
            throw new IllegalStateException("the start gate was not reached", startGateFailure);
        }
    }
}
