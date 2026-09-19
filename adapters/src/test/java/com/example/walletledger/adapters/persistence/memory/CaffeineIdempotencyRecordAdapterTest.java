package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.RejectionReason;
import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.core.model.TransferOutcome;
import com.example.walletledger.usecases.spi.ClaimResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeineIdempotencyRecordAdapterTest {

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final IdempotencyKey KEY = IdempotencyKey.of("key-1");
    private static final IdempotencyKey UNKNOWN_KEY = IdempotencyKey.of("key-unknown");
    private static final String FINGERPRINT = "source|target|25.00|EUR";
    private static final String OTHER_FINGERPRINT = "source|target|99.00|EUR";
    private static final Duration RETENTION = Duration.ofMinutes(5);
    private static final Duration WAIT_BUDGET = Duration.ofSeconds(5);
    private static final Duration EVICTION_DEADLINE = Duration.ofSeconds(10);
    private static final long MAXIMUM_RECORDS = 1_000L;

    private CaffeineIdempotencyRecordAdapter idempotencyRecords;

    @BeforeEach
    void createAdapter() {
        idempotencyRecords = new CaffeineIdempotencyRecordAdapter(MAXIMUM_RECORDS, RETENTION);
    }

    @Test
    void shouldGrantTheClaimWhenKeyIsSeenForTheFirstTime() {
        // when
        var claim = idempotencyRecords.claim(KEY, FINGERPRINT);

        // then
        assertThat(claim).isInstanceOf(ClaimResult.Claimed.class);
    }

    @Test
    void shouldReportInFlightWhenSameFingerprintIsClaimedBeforeSettlement() {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);

        // when
        var duplicateClaim = idempotencyRecords.claim(KEY, FINGERPRINT);

        // then
        assertThat(duplicateClaim).isInstanceOf(ClaimResult.InFlight.class);
    }

    @Test
    void shouldReplayTheStoredOutcomeWhenSameFingerprintIsClaimedAfterSettlement() {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);
        idempotencyRecords.settle(KEY, appliedOutcome());

        // when
        var duplicateClaim = idempotencyRecords.claim(KEY, FINGERPRINT);

        // then
        assertThat(duplicateClaim).isInstanceOfSatisfying(ClaimResult.Replayed.class,
                replayed -> assertThat(replayed.outcome()).isEqualTo(appliedOutcome()));
    }

    @Test
    void shouldReportConflictWhenFingerprintDiffers() {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);

        // when
        var conflictingClaim = idempotencyRecords.claim(KEY, OTHER_FINGERPRINT);

        // then
        assertThat(conflictingClaim).isInstanceOf(ClaimResult.Conflicting.class);
    }

    @Test
    void shouldReportConflictWhenFingerprintDiffersAfterSettlement() {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);
        idempotencyRecords.settle(KEY, appliedOutcome());

        // when
        var conflictingClaim = idempotencyRecords.claim(KEY, OTHER_FINGERPRINT);

        // then
        assertThat(conflictingClaim).isInstanceOf(ClaimResult.Conflicting.class);
    }

    @Test
    void shouldReplayRejectionsWhenSettledOutcomeIsARejection() {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);
        var rejection = new TransferOutcome.Rejected(RejectionReason.INSUFFICIENT_FUNDS, "not enough funds");
        idempotencyRecords.settle(KEY, rejection);

        // when
        var duplicateClaim = idempotencyRecords.claim(KEY, FINGERPRINT);

        // then
        assertThat(duplicateClaim).isInstanceOfSatisfying(ClaimResult.Replayed.class,
                replayed -> assertThat(replayed.outcome()).isEqualTo(rejection));
    }

    @Test
    void shouldReturnTheOutcomeWhenSettlementArrivesWithinTheBudget() throws InterruptedException {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);
        var settlers = Executors.newSingleThreadExecutor();
        var waiterStarted = new CountDownLatch(1);

        // when
        settlers.execute(() -> {
            awaitQuietly(waiterStarted);
            idempotencyRecords.settle(KEY, appliedOutcome());
        });
        waiterStarted.countDown();
        var settledOutcome = idempotencyRecords.awaitSettlement(KEY, WAIT_BUDGET);

        // then
        assertThat(settledOutcome).contains(appliedOutcome());
        shutdown(settlers);
    }

    @Test
    void shouldReturnEmptyWhenSettlementDoesNotArriveWithinTheBudget() {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);

        // when
        var settledOutcome = idempotencyRecords.awaitSettlement(KEY, Duration.ofMillis(50));

        // then
        assertThat(settledOutcome).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenKeyWasNeverClaimed() {
        // when
        var settledOutcome = idempotencyRecords.awaitSettlement(UNKNOWN_KEY, Duration.ofMillis(50));

        // then
        assertThat(settledOutcome).isEmpty();
    }

    @Test
    void shouldAllowARenewedClaimWhenTheRecordIsReleased() {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);
        idempotencyRecords.release(KEY);

        // when
        var renewedClaim = idempotencyRecords.claim(KEY, FINGERPRINT);

        // then
        assertThat(renewedClaim).isInstanceOf(ClaimResult.Claimed.class);
    }

    @Test
    void shouldReleaseWaitersWhenTheRecordIsAbandoned() throws InterruptedException {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);
        var releasers = Executors.newSingleThreadExecutor();

        // when
        releasers.execute(() -> idempotencyRecords.release(KEY));
        var settledOutcome = idempotencyRecords.awaitSettlement(KEY, WAIT_BUDGET);

        // then
        assertThat(settledOutcome).isEmpty();
        shutdown(releasers);
    }

    @Test
    void shouldIgnoreSettlementWhenKeyWasNeverClaimed() {
        // when
        idempotencyRecords.settle(UNKNOWN_KEY, appliedOutcome());

        // then
        assertThat(idempotencyRecords.countRecords()).isZero();
    }

    @Test
    void shouldCountRetainedRecordsWhenKeysAreClaimed() {
        // given
        idempotencyRecords.claim(KEY, FINGERPRINT);

        // when
        idempotencyRecords.claim(IdempotencyKey.of("key-2"), FINGERPRINT);

        // then
        assertThat(idempotencyRecords.countRecords()).isEqualTo(2);
    }

    @Test
    void shouldEvictRecordsWhenMaximumSizeIsExceeded() {
        // given
        var boundedRecords = new CaffeineIdempotencyRecordAdapter(10L, RETENTION);

        // when
        for (var index = 0; index < 5_000; index++) {
            boundedRecords.claim(IdempotencyKey.of("key-" + index), FINGERPRINT);
        }

        // then
        assertThat(awaitRecordCountAtMost(boundedRecords, 10L)).isTrue();
    }

    private static boolean awaitRecordCountAtMost(CaffeineIdempotencyRecordAdapter records, long maximumRecords) {
        var deadline = System.nanoTime() + EVICTION_DEADLINE.toNanos();
        while (System.nanoTime() < deadline) {
            if (records.countRecords() <= maximumRecords) {
                return true;
            }
            Thread.onSpinWait();
        }
        return false;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shutdown(ExecutorService executorService) throws InterruptedException {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static TransferOutcome appliedOutcome() {
        return new TransferOutcome.Applied(TransferId.of("transfer-1"), AccountId.of("source"),
                AccountId.of("target"), Money.of(new BigDecimal("25.00"), EURO),
                Instant.parse("2026-06-06T09:00:00Z"));
    }
}
