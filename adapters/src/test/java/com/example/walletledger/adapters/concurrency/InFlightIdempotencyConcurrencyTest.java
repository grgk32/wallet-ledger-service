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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InFlightIdempotencyConcurrencyTest {

    private static final String SHARED_IDEMPOTENCY_KEY = "retry-while-in-flight";
    private static final String TRANSFER_AMOUNT = "25.00";
    private static final Duration CRITICAL_SECTION_ENTRY_BUDGET = Duration.ofSeconds(10);
    private static final Duration IN_FLIGHT_OBSERVATION_WINDOW = Duration.ofMillis(300);
    private static final Duration COMPLETION_BUDGET = Duration.ofSeconds(30);

    private LedgerTestFixture ledger;
    private AccountId sourceAccountId;
    private AccountId targetAccountId;
    private ExecutorService clients;

    @BeforeEach
    void openAccounts() {
        ledger = new LedgerTestFixture();
        sourceAccountId = ledger.openAccount("100.00");
        targetAccountId = ledger.openAccount("0.00");
        ledger.latchingLedger().armFor(Set.of(sourceAccountId, targetAccountId));
        clients = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopClients() {
        clients.shutdownNow();
    }

    @Test
    void shouldApplyTransferOnceWhenRetryArrivesInFlight() throws Exception {
        // when
        runOriginalAndRetry();

        // then
        assertThat(ledger.balanceOf(sourceAccountId)).isEqualTo(LedgerTestFixture.euros("75.00"));
    }

    @Test
    void shouldCreditTheTargetOnceWhenRetryArrivesInFlight() throws Exception {
        // when
        runOriginalAndRetry();

        // then
        assertThat(ledger.balanceOf(targetAccountId)).isEqualTo(LedgerTestFixture.euros("25.00"));
    }

    @Test
    void shouldReturnTheOriginalTransferIdentifierWhenRetryArrivesInFlight() throws Exception {
        // when
        var run = runOriginalAndRetry();

        // then
        assertThat(run.retry().transferId()).isEqualTo(run.original().transferId());
    }

    @Test
    void shouldMarkTheRetryAsReplayedWhenRetryArrivesInFlight() throws Exception {
        // when
        var run = runOriginalAndRetry();

        // then
        assertThat(run.retry().replayed()).isTrue();
    }

    @Test
    void shouldNotMarkTheOriginalAsReplayedWhenRetryArrivesInFlight() throws Exception {
        // when
        var run = runOriginalAndRetry();

        // then
        assertThat(run.original().replayed()).isFalse();
    }

    @Test
    void shouldHoldTheRetryWhenTheOriginalRequestIsStillRunning() throws Exception {
        // when
        var run = runOriginalAndRetry();

        // then
        assertThat(run.retryWasStillWaitingWhileOriginalHeldTheLedger()).isTrue();
    }

    @Test
    void shouldPostExactlyOnePostingWhenRetryArrivesInFlight() throws Exception {
        // when
        runOriginalAndRetry();

        // then
        assertThat(ledger.entriesOf(sourceAccountId)).hasSize(2);
    }

    private RetryRun runOriginalAndRetry() throws Exception {
        var original = clients.submit(this::postSharedTransfer);
        assertThat(ledger.latchingLedger().awaitCriticalSectionEntry(CRITICAL_SECTION_ENTRY_BUDGET)).isTrue();

        var retry = clients.submit(this::postSharedTransfer);
        var retryWasStillWaiting = isStillRunning(retry);
        ledger.latchingLedger().releaseCriticalSection();

        return new RetryRun(awaitCompletion(original), awaitCompletion(retry), retryWasStillWaiting);
    }

    private TransferReceipt postSharedTransfer() {
        return ledger.transfer(sourceAccountId, targetAccountId, TRANSFER_AMOUNT, SHARED_IDEMPOTENCY_KEY);
    }

    private static boolean isStillRunning(Future<TransferReceipt> pendingTransfer) {
        try {
            pendingTransfer.get(IN_FLIGHT_OBSERVATION_WINDOW.toMillis(), TimeUnit.MILLISECONDS);
            return false;
        } catch (TimeoutException stillWaiting) {
            return true;
        } catch (Exception unexpectedFailure) {
            throw new IllegalStateException("the retry failed while the original was in flight", unexpectedFailure);
        }
    }

    private static TransferReceipt awaitCompletion(Future<TransferReceipt> pendingTransfer) throws Exception {
        return pendingTransfer.get(COMPLETION_BUDGET.toSeconds(), TimeUnit.SECONDS);
    }

    private record RetryRun(TransferReceipt original,
                            TransferReceipt retry,
                            boolean retryWasStillWaitingWhileOriginalHeldTheLedger) {
    }
}
