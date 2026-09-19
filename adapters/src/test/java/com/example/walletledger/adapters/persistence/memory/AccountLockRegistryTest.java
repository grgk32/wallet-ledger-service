package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.AccountId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class AccountLockRegistryTest {

    private static final AccountId ACCOUNT_A = AccountId.of("account-a");
    private static final AccountId ACCOUNT_B = AccountId.of("account-b");
    private static final AccountId ACCOUNT_C = AccountId.of("account-c");
    private static final int OPPOSING_ACQUISITION_ROUNDS = 5_000;

    private AccountLockRegistry accountLockRegistry;

    @BeforeEach
    void createRegistry() {
        accountLockRegistry = new AccountLockRegistry();
    }

    @Test
    void shouldAcquireLocksInCanonicalOrderWhenParticipantsArriveUnordered() {
        // given
        var ascendingAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_A, ACCOUNT_B, ACCOUNT_C));
        accountLockRegistry.releaseAll(ascendingAcquisition);

        // when
        var descendingAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_C, ACCOUNT_B, ACCOUNT_A));
        accountLockRegistry.releaseAll(descendingAcquisition);

        // then
        assertThat(descendingAcquisition).containsExactlyElementsOf(ascendingAcquisition);
    }

    @Test
    void shouldReuseTheSameLockWhenTheAccountIsRequestedAgain() {
        // given
        var firstAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_A));
        accountLockRegistry.releaseAll(firstAcquisition);

        // when
        var secondAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_A));
        accountLockRegistry.releaseAll(secondAcquisition);

        // then
        assertThat(secondAcquisition.get(0)).isSameAs(firstAcquisition.get(0));
    }

    @Test
    void shouldFreeEveryLockWhenAcquisitionIsUnwound() {
        // given
        var acquiredLocks = accountLockRegistry.acquireAll(List.of(ACCOUNT_A, ACCOUNT_B, ACCOUNT_C));

        // when
        accountLockRegistry.releaseAll(acquiredLocks);

        // then
        assertThat(acquiredLocks).extracting(ReentrantLock::isLocked).containsOnly(false);
    }

    @Test
    void shouldSupportReentrantAcquisitionWhenTheSameThreadLocksTwice() {
        // given
        var outerAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_A));

        // when
        var innerAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_A));

        // then
        assertThat(innerAcquisition.get(0).getHoldCount()).isEqualTo(2);
        accountLockRegistry.releaseAll(innerAcquisition);
        accountLockRegistry.releaseAll(outerAcquisition);
    }

    @Test
    void shouldReleaseTheOuterAcquisitionWhenReentrantHoldsAreUnwound() {
        // given
        var outerAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_A));
        var innerAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_A));

        // when
        accountLockRegistry.releaseAll(innerAcquisition);
        accountLockRegistry.releaseAll(outerAcquisition);

        // then
        assertThat(outerAcquisition.get(0).isLocked()).isFalse();
    }

    @Test
    void shouldReturnNoLocksWhenParticipantCollectionIsEmpty() {
        // when
        var acquiredLocks = accountLockRegistry.acquireAll(List.of());

        // then
        assertThat(acquiredLocks).isEmpty();
        accountLockRegistry.releaseAll(acquiredLocks);
    }

    @Test
    void shouldBlockCompetingThreadWhenAccountLockIsAlreadyHeld() throws InterruptedException {
        // given
        var acquiredLocks = accountLockRegistry.acquireAll(List.of(ACCOUNT_A));
        var competitorAcquiredLock = new CountDownLatch(1);
        var competitors = Executors.newSingleThreadExecutor();

        // when
        competitors.execute(() -> {
            var competingAcquisition = accountLockRegistry.acquireAll(List.of(ACCOUNT_A));
            competitorAcquiredLock.countDown();
            accountLockRegistry.releaseAll(competingAcquisition);
        });

        // then
        assertThat(competitorAcquiredLock.await(200, TimeUnit.MILLISECONDS)).isFalse();
        accountLockRegistry.releaseAll(acquiredLocks);
        assertThat(competitorAcquiredLock.await(5, TimeUnit.SECONDS)).isTrue();
        shutdown(competitors);
    }

    @Test
    void shouldNotDeadlockWhenTwoThreadsRequestOverlappingAccountsInOpposingOrder() {
        // given
        var competitors = Executors.newFixedThreadPool(2);
        var ascending = List.of(ACCOUNT_A, ACCOUNT_B);
        var descending = List.of(ACCOUNT_B, ACCOUNT_A);

        // when
        Runnable ascendingRounds = () -> repeatAcquisition(ascending);
        Runnable descendingRounds = () -> repeatAcquisition(descending);

        // then
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            var ascendingRun = competitors.submit(ascendingRounds);
            var descendingRun = competitors.submit(descendingRounds);
            ascendingRun.get();
            descendingRun.get();
        });
        competitors.shutdownNow();
    }

    @Test
    void shouldCountTrackedAccountsWhenLocksHaveBeenMaterialised() {
        // given
        var acquiredLocks = accountLockRegistry.acquireAll(List.of(ACCOUNT_A, ACCOUNT_B));

        // when
        accountLockRegistry.releaseAll(acquiredLocks);

        // then
        assertThat(accountLockRegistry.countTrackedAccounts()).isEqualTo(2);
    }

    private void repeatAcquisition(List<AccountId> participants) {
        for (var round = 0; round < OPPOSING_ACQUISITION_ROUNDS; round++) {
            var acquiredLocks = accountLockRegistry.acquireAll(participants);
            accountLockRegistry.releaseAll(acquiredLocks);
        }
    }

    private static void shutdown(ExecutorService executorService) throws InterruptedException {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
}
