package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.exception.TransferRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyConservationConcurrencyTest {

    private static final int ACCOUNT_COUNT = 16;
    private static final int THREAD_COUNT = 32;
    private static final int TRANSFER_COUNT = 5_000;
    private static final String OPENING_BALANCE = "1000.00";
    private static final long RANDOM_SEED = 20260919L;

    private LedgerTestFixture ledger;
    private List<AccountId> accountIds;
    private ExecutorService clients;

    @BeforeEach
    void openAccounts() {
        ledger = new LedgerTestFixture();
        accountIds = new ArrayList<>(ACCOUNT_COUNT);
        for (var index = 0; index < ACCOUNT_COUNT; index++) {
            accountIds.add(ledger.openAccount(OPENING_BALANCE));
        }
        clients = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @AfterEach
    void stopClients() {
        clients.shutdownNow();
    }

    @Test
    void shouldConserveTotalBalanceWhenManyThreadsTransferAcrossManyAccounts() throws Exception {
        // given
        var openingTotal = ledger.totalBalance();

        // when
        runRandomisedTransfers();

        // then
        assertThat(ledger.totalBalance()).isEqualTo(openingTotal);
    }

    @Test
    void shouldKeepEveryBalanceNonNegativeWhenManyThreadsTransferAcrossManyAccounts() throws Exception {
        // when
        runRandomisedTransfers();

        // then
        assertThat(ledger.allAccounts()).allSatisfy(
                account -> assertThat(account.balance().amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }

    @Test
    void shouldSettleEveryRequestWhenManyThreadsTransferAcrossManyAccounts() throws Exception {
        // when
        var tally = runRandomisedTransfers();

        // then
        assertThat(tally.settledRequests()).isEqualTo(TRANSFER_COUNT);
    }

    @Test
    void shouldBalanceEveryPostingWhenManyThreadsTransferAcrossManyAccounts() throws Exception {
        // when
        var tally = runRandomisedTransfers();

        // then
        assertThat(ledger.allEntries()).hasSize(2 * (ACCOUNT_COUNT + tally.appliedTransfers()));
    }

    private TransferTally runRandomisedTransfers() throws Exception {
        var startGate = new CyclicBarrier(THREAD_COUNT);
        var tally = new TransferTally();
        var runs = new ArrayList<Future<?>>(THREAD_COUNT);
        for (var thread = 0; thread < THREAD_COUNT; thread++) {
            var threadIndex = thread;
            runs.add(clients.submit(() -> {
                awaitStartGate(startGate);
                postTransfersFor(threadIndex, tally);
                return null;
            }));
        }
        for (var run : runs) {
            run.get(180, TimeUnit.SECONDS);
        }
        return tally;
    }

    private void postTransfersFor(int threadIndex, TransferTally tally) {
        var random = new Random(RANDOM_SEED + threadIndex);
        for (var request = threadIndex; request < TRANSFER_COUNT; request += THREAD_COUNT) {
            var sourceIndex = random.nextInt(ACCOUNT_COUNT);
            var targetIndex = (sourceIndex + 1 + random.nextInt(ACCOUNT_COUNT - 1)) % ACCOUNT_COUNT;
            var amount = new BigDecimal(1 + random.nextInt(10)).setScale(2).toPlainString();
            postSingleTransfer(sourceIndex, targetIndex, amount, "conservation-" + request, tally);
        }
    }

    private void postSingleTransfer(int sourceIndex, int targetIndex, String amount, String idempotencyKey,
                                    TransferTally tally) {
        try {
            ledger.transfer(accountIds.get(sourceIndex), accountIds.get(targetIndex), amount, idempotencyKey);
            tally.countApplied();
        } catch (TransferRejectedException rejection) {
            tally.countRejected();
        }
    }

    private static void awaitStartGate(CyclicBarrier startGate) {
        try {
            startGate.await(60, TimeUnit.SECONDS);
        } catch (Exception startGateFailure) {
            throw new IllegalStateException("the start gate was not reached", startGateFailure);
        }
    }

    private static final class TransferTally {

        private final AtomicInteger appliedTransfers = new AtomicInteger();
        private final AtomicInteger rejectedTransfers = new AtomicInteger();

        private void countApplied() {
            appliedTransfers.incrementAndGet();
        }

        private void countRejected() {
            rejectedTransfers.incrementAndGet();
        }

        private int appliedTransfers() {
            return appliedTransfers.get();
        }

        private int settledRequests() {
            return appliedTransfers.get() + rejectedTransfers.get();
        }
    }
}
