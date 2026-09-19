package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleEntryIntegrityTest {

    private static final int ACCOUNT_COUNT = 8;
    private static final int THREAD_COUNT = 8;
    private static final int TRANSFERS_PER_THREAD = 200;
    private static final String OPENING_BALANCE = "5000.00";
    private static final String TRANSFER_AMOUNT = "3.00";

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
    void shouldRecordExactlyOneDebitAndOneCreditWhenTransferIsApplied() throws Exception {
        // when
        runConcurrentTransfers();

        // then
        assertThat(entriesByTransfer()).allSatisfy((transferId, entries) ->
                assertThat(entries).extracting(LedgerEntry::direction).hasSize(2).doesNotHaveDuplicates());
    }

    @Test
    void shouldRecordEqualAmountsOnBothLegsWhenTransferIsApplied() throws Exception {
        // when
        runConcurrentTransfers();

        // then
        assertThat(entriesByTransfer()).allSatisfy((transferId, entries) ->
                assertThat(entries).extracting(LedgerEntry::amount).containsOnly(entries.get(0).amount()));
    }

    @Test
    void shouldReferenceTwoDistinctAccountsWhenTransferIsApplied() throws Exception {
        // when
        runConcurrentTransfers();

        // then
        assertThat(entriesByTransfer()).allSatisfy((transferId, entries) ->
                assertThat(entries).extracting(LedgerEntry::accountId).doesNotHaveDuplicates());
    }

    @Test
    void shouldReproduceLiveBalancesWhenJournalIsReplayed() throws Exception {
        // when
        runConcurrentTransfers();

        // then
        assertThat(ledger.allAccounts()).allSatisfy(
                account -> assertThat(replayJournalOf(account.accountId())).isEqualTo(account.balance()));
    }

    @Test
    void shouldBalanceDebitsAgainstCreditsWhenJournalIsReplayedInFull() throws Exception {
        // when
        runConcurrentTransfers();

        // then
        assertThat(netAmountOf(ledger.allEntries())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Money replayJournalOf(AccountId accountId) {
        return Money.of(netAmountOf(ledger.entriesOf(accountId)), LedgerTestFixture.EURO);
    }

    private static BigDecimal netAmountOf(List<LedgerEntry> entries) {
        return entries.stream()
                .map(entry -> entry.isCredit() ? entry.amount().amount() : entry.amount().amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<TransferId, List<LedgerEntry>> entriesByTransfer() {
        return ledger.allEntries().stream().collect(Collectors.groupingBy(LedgerEntry::transferId));
    }

    private void runConcurrentTransfers() throws Exception {
        var startGate = new CyclicBarrier(THREAD_COUNT);
        var runs = new ArrayList<Future<?>>(THREAD_COUNT);
        for (var thread = 0; thread < THREAD_COUNT; thread++) {
            var threadIndex = thread;
            runs.add(clients.submit(() -> {
                awaitStartGate(startGate);
                postTransfersFor(threadIndex);
                return null;
            }));
        }
        for (var run : runs) {
            run.get(120, TimeUnit.SECONDS);
        }
    }

    private void postTransfersFor(int threadIndex) {
        for (var transfer = 0; transfer < TRANSFERS_PER_THREAD; transfer++) {
            var sourceIndex = (threadIndex + transfer) % ACCOUNT_COUNT;
            var targetIndex = (sourceIndex + 1) % ACCOUNT_COUNT;
            ledger.transfer(accountIds.get(sourceIndex), accountIds.get(targetIndex), TRANSFER_AMOUNT,
                    "double-entry-" + threadIndex + "-" + transfer);
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
