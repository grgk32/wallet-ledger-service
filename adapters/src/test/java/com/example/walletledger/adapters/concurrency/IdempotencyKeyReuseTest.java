package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.api.TransferReceipt;
import com.example.walletledger.usecases.exception.IdempotencyKeyReusedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyReuseTest {

    private static final String SHARED_IDEMPOTENCY_KEY = "shared-key";
    private static final int COMPETING_CLIENTS = 32;

    private LedgerTestFixture ledger;
    private AccountId sourceAccountId;
    private AccountId targetAccountId;
    private ExecutorService clients;

    @BeforeEach
    void openAccounts() {
        ledger = new LedgerTestFixture();
        sourceAccountId = ledger.openAccount("100.00");
        targetAccountId = ledger.openAccount("0.00");
        clients = Executors.newFixedThreadPool(COMPETING_CLIENTS);
    }

    @AfterEach
    void stopClients() {
        clients.shutdownNow();
    }

    @Test
    void shouldRejectRequestWhenIdempotencyKeyReusedWithDifferentPayload() {
        // given
        ledger.transfer(sourceAccountId, targetAccountId, "25.00", SHARED_IDEMPOTENCY_KEY);

        // when
        var reuse = assertThatThrownBy(
                () -> ledger.transfer(sourceAccountId, targetAccountId, "30.00", SHARED_IDEMPOTENCY_KEY));

        // then
        reuse.isInstanceOf(IdempotencyKeyReusedException.class)
                .hasMessageContaining(SHARED_IDEMPOTENCY_KEY);
    }

    @Test
    void shouldRejectRequestWhenIdempotencyKeyReusedWithADifferentTarget() {
        // given
        var otherTargetId = ledger.openAccount("0.00");
        ledger.transfer(sourceAccountId, targetAccountId, "25.00", SHARED_IDEMPOTENCY_KEY);

        // when
        var reuse = assertThatThrownBy(
                () -> ledger.transfer(sourceAccountId, otherTargetId, "25.00", SHARED_IDEMPOTENCY_KEY));

        // then
        reuse.isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void shouldLeaveBalancesUnchangedWhenIdempotencyKeyIsReused() {
        // given
        ledger.transfer(sourceAccountId, targetAccountId, "25.00", SHARED_IDEMPOTENCY_KEY);

        // when
        assertThatThrownBy(() -> ledger.transfer(sourceAccountId, targetAccountId, "30.00", SHARED_IDEMPOTENCY_KEY))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        // then
        assertThat(ledger.balanceOf(sourceAccountId)).isEqualTo(LedgerTestFixture.euros("75.00"));
    }

    @Test
    void shouldApplyTheTransferOnceWhenManyClientsShareOneKey() throws Exception {
        // when
        runConcurrentRetries();

        // then
        assertThat(ledger.balanceOf(targetAccountId)).isEqualTo(LedgerTestFixture.euros("25.00"));
    }

    @Test
    void shouldReturnOneTransferIdentifierWhenManyClientsShareOneKey() throws Exception {
        // when
        var receipts = runConcurrentRetries();

        // then
        assertThat(receipts).extracting(TransferReceipt::transferId).containsOnly(receipts.get(0).transferId());
    }

    @Test
    void shouldMarkExactlyOneReceiptAsAppliedWhenManyClientsShareOneKey() throws Exception {
        // when
        var receipts = runConcurrentRetries();

        // then
        assertThat(receipts).filteredOn(receipt -> !receipt.replayed()).hasSize(1);
    }

    private List<TransferReceipt> runConcurrentRetries() throws Exception {
        var startGate = new CyclicBarrier(COMPETING_CLIENTS);
        var attempts = new ArrayList<Future<TransferReceipt>>(COMPETING_CLIENTS);
        for (var client = 0; client < COMPETING_CLIENTS; client++) {
            attempts.add(clients.submit(() -> {
                awaitStartGate(startGate);
                return ledger.transfer(sourceAccountId, targetAccountId, "25.00", SHARED_IDEMPOTENCY_KEY);
            }));
        }
        var receipts = new ArrayList<TransferReceipt>(COMPETING_CLIENTS);
        for (var attempt : attempts) {
            receipts.add(attempt.get(60, TimeUnit.SECONDS));
        }
        return receipts;
    }

    private static void awaitStartGate(CyclicBarrier startGate) {
        try {
            startGate.await(60, TimeUnit.SECONDS);
        } catch (Exception startGateFailure) {
            throw new IllegalStateException("the start gate was not reached", startGateFailure);
        }
    }
}
