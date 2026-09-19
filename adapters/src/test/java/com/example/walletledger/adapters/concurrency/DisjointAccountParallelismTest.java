package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.api.TransferReceipt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class DisjointAccountParallelismTest {

    private static final String OPENING_BALANCE = "100.00";
    private static final String TRANSFER_AMOUNT = "25.00";
    private static final Duration CRITICAL_SECTION_ENTRY_BUDGET = Duration.ofSeconds(10);
    private static final Duration CONTENDED_OBSERVATION_WINDOW = Duration.ofMillis(300);
    private static final Duration DISJOINT_COMPLETION_BUDGET = Duration.ofSeconds(10);

    private LedgerTestFixture ledger;
    private AccountId contendedSourceId;
    private AccountId contendedTargetId;
    private AccountId disjointSourceId;
    private AccountId disjointTargetId;
    private ExecutorService clients;

    @BeforeEach
    void openAccounts() {
        ledger = new LedgerTestFixture();
        contendedSourceId = ledger.openAccount(OPENING_BALANCE);
        contendedTargetId = ledger.openAccount(OPENING_BALANCE);
        disjointSourceId = ledger.openAccount(OPENING_BALANCE);
        disjointTargetId = ledger.openAccount(OPENING_BALANCE);
        clients = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopClients() {
        clients.shutdownNow();
    }

    @Test
    void shouldNotDelayDisjointTransferWhenAnotherTransferHoldsItsLocks() throws Exception {
        // given
        var latchedTransfer = startLatchedContendedTransfer();

        // when
        var disjointTransfer = clients.submit(() -> ledger.transfer(disjointSourceId, disjointTargetId,
                TRANSFER_AMOUNT, "disjoint-transfer"));

        // then
        assertThat(disjointTransfer.get(DISJOINT_COMPLETION_BUDGET.toSeconds(), TimeUnit.SECONDS).replayed())
                .isFalse();
        releaseAndAwait(latchedTransfer);
    }

    @Test
    void shouldCommitDisjointTransferWhileContendedAccountsRemainLocked() throws Exception {
        // given
        var latchedTransfer = startLatchedContendedTransfer();

        // when
        clients.submit(() -> ledger.transfer(disjointSourceId, disjointTargetId, TRANSFER_AMOUNT, "disjoint-commit"))
                .get(DISJOINT_COMPLETION_BUDGET.toSeconds(), TimeUnit.SECONDS);

        // then
        assertThat(ledger.balanceOf(disjointTargetId)).isEqualTo(LedgerTestFixture.euros("125.00"));
        releaseAndAwait(latchedTransfer);
    }

    @Test
    void shouldDelayOverlappingTransferWhenAnotherTransferHoldsItsLocks() throws Exception {
        // given
        var latchedTransfer = startLatchedContendedTransfer();

        // when
        var overlappingTransfer = clients.submit(() -> ledger.transfer(contendedTargetId, disjointTargetId,
                TRANSFER_AMOUNT, "overlapping-transfer"));

        // then
        assertThat(isStillRunning(overlappingTransfer)).isTrue();
        releaseAndAwait(latchedTransfer);
        overlappingTransfer.get(DISJOINT_COMPLETION_BUDGET.toSeconds(), TimeUnit.SECONDS);
    }

    private Future<TransferReceipt> startLatchedContendedTransfer() throws InterruptedException {
        ledger.latchingLedger().armFor(Set.of(contendedSourceId, contendedTargetId));
        var latchedTransfer = clients.submit(() -> ledger.transfer(contendedSourceId, contendedTargetId,
                TRANSFER_AMOUNT, "contended-transfer"));
        assertThat(ledger.latchingLedger().awaitCriticalSectionEntry(CRITICAL_SECTION_ENTRY_BUDGET)).isTrue();
        return latchedTransfer;
    }

    private void releaseAndAwait(Future<TransferReceipt> latchedTransfer) throws Exception {
        ledger.latchingLedger().releaseCriticalSection();
        latchedTransfer.get(DISJOINT_COMPLETION_BUDGET.toSeconds(), TimeUnit.SECONDS);
    }

    private static boolean isStillRunning(Future<TransferReceipt> pendingTransfer) {
        try {
            pendingTransfer.get(CONTENDED_OBSERVATION_WINDOW.toMillis(), TimeUnit.MILLISECONDS);
            return false;
        } catch (TimeoutException stillWaiting) {
            return true;
        } catch (Exception unexpectedFailure) {
            throw new IllegalStateException("the overlapping transfer failed", unexpectedFailure);
        }
    }
}
