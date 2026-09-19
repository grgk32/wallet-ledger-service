package com.example.walletledger.usecases.exception;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.RejectionReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationExceptionTest {

    private static final AccountId ACCOUNT_ID = AccountId.of("account-1");
    private static final IdempotencyKey IDEMPOTENCY_KEY = IdempotencyKey.of("key-1");

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("applicationFailures")
    void shouldStayUncheckedWhenUseCaseFails(String failureName, ApplicationException exception) {
        // then
        assertThat(exception).isInstanceOf(RuntimeException.class).isInstanceOf(ApplicationException.class);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("applicationFailures")
    void shouldExplainTheFailureWhenUseCaseFails(String failureName, ApplicationException exception) {
        // then
        assertThat(exception.getMessage()).isNotBlank();
    }

    @Test
    void shouldExposeTheMissingAccountWhenAccountIsNotFound() {
        // given
        var exception = new AccountNotFoundException(ACCOUNT_ID);

        // then
        assertThat(exception.accountId()).isEqualTo(ACCOUNT_ID);
    }

    @Test
    void shouldNameTheMissingAccountWhenAccountIsNotFound() {
        // given
        var exception = new AccountNotFoundException(ACCOUNT_ID);

        // then
        assertThat(exception).hasMessage("account account-1 does not exist");
    }

    @Test
    void shouldNameTheReusedKeyWhenPayloadDiffers() {
        // given
        var exception = new IdempotencyKeyReusedException(IDEMPOTENCY_KEY);

        // then
        assertThat(exception).hasMessageContaining("key-1").hasMessageContaining("different request payload");
    }

    @Test
    void shouldExposeTheWaitBudgetWhenOriginalRequestIsStillRunning() {
        // given
        var exception = new TransferInProgressException(IDEMPOTENCY_KEY, Duration.ofMillis(250));

        // then
        assertThat(exception.waitBudget()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void shouldReportTheWaitBudgetInMillisecondsWhenOriginalRequestIsStillRunning() {
        // given
        var exception = new TransferInProgressException(IDEMPOTENCY_KEY, Duration.ofSeconds(2));

        // then
        assertThat(exception).hasMessageContaining("2000 ms");
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(RejectionReason.class)
    void shouldExposeTheRejectionReasonWhenTransferIsRefused(RejectionReason reason) {
        // given
        var exception = new TransferRejectedException(reason, "detail for " + reason.name());

        // then
        assertThat(exception.reason()).isEqualTo(reason);
    }

    @Test
    void shouldReportTheRejectionDetailAsMessageWhenTransferIsRefused() {
        // given
        var exception = new TransferRejectedException(RejectionReason.INSUFFICIENT_FUNDS, "balance too low");

        // then
        assertThat(exception).hasMessage("balance too low");
    }

    private static Stream<Arguments> applicationFailures() {
        return Stream.of(
                Arguments.of("account not found", new AccountNotFoundException(ACCOUNT_ID)),
                Arguments.of("idempotency key reused", new IdempotencyKeyReusedException(IDEMPOTENCY_KEY)),
                Arguments.of("transfer in progress",
                        new TransferInProgressException(IDEMPOTENCY_KEY, Duration.ofMillis(200))),
                Arguments.of("transfer rejected",
                        new TransferRejectedException(RejectionReason.INSUFFICIENT_FUNDS, "balance too low")));
    }
}
