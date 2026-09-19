package com.example.walletledger.integration;

import com.example.walletledger.adapters.web.dto.AccountResponse;
import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.EntryResponse;
import com.example.walletledger.adapters.web.dto.ErrorCode;
import com.example.walletledger.adapters.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static com.example.walletledger.integration.LedgerApiClient.IDEMPOTENCY_REPLAYED_HEADER;
import static com.example.walletledger.integration.LedgerApiClient.nextIdempotencyKey;
import static com.example.walletledger.integration.LedgerApiClient.transferOf;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WalletLedgerApiIntegrationTest {

    private static final String UNKNOWN_ACCOUNT_ID = "3f0d1c7a-5b62-4e18-9d4c-8a2e6f5b3c71";

    @Autowired
    private TestRestTemplate restTemplate;

    private LedgerApiClient ledgerApi;

    @BeforeEach
    void createApiClient() {
        ledgerApi = new LedgerApiClient(restTemplate);
    }

    @Test
    void shouldReturnTheOpeningBalanceWhenAccountIsCreated() {
        // when
        var response = ledgerApi.createAccount("customer-opening", "100.00");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody())
                .extracting(AccountResponse::ownerReference, AccountResponse::currency, AccountResponse::balance)
                .containsExactly("customer-opening", "EUR", new BigDecimal("100.00"));
    }

    @Test
    void shouldMoveMoneyBetweenAccountsWhenTransferIsPosted() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-target", "0.00");

        // when
        var response = ledgerApi.postTransfer(nextIdempotencyKey(),
                transferOf(sourceAccountId, targetAccountId, "25.00"), TransferResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(IDEMPOTENCY_REPLAYED_HEADER)).isEqualTo("false");
        assertThat(ledgerApi.balanceOf(sourceAccountId)).isEqualByComparingTo("75.00");
        assertThat(ledgerApi.balanceOf(targetAccountId)).isEqualByComparingTo("25.00");
    }

    @Test
    void shouldRecordOneDebitAndOneCreditWhenTransferIsApplied() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-journal-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-journal-target", "0.00");
        var transferId = postedTransferId(sourceAccountId, targetAccountId, "40.00");

        // when
        var sourceEntries = ledgerApi.readEntries(sourceAccountId);
        var targetEntries = ledgerApi.readEntries(targetAccountId);

        // then
        assertThat(entriesOfTransfer(sourceEntries, transferId))
                .singleElement()
                .extracting(EntryResponse::direction, EntryResponse::amount)
                .containsExactly("DEBIT", new BigDecimal("40.00"));
        assertThat(entriesOfTransfer(targetEntries, transferId))
                .singleElement()
                .extracting(EntryResponse::direction, EntryResponse::amount)
                .containsExactly("CREDIT", new BigDecimal("40.00"));
    }

    @Test
    void shouldRecordTheFundingCreditWhenAccountIsOpenedWithABalance() {
        // given
        var accountId = ledgerApi.openAccount("customer-funded", "100.00");

        // when
        var entries = ledgerApi.readEntries(accountId);

        // then
        assertThat(entries)
                .singleElement()
                .extracting(EntryResponse::direction, EntryResponse::amount)
                .containsExactly("CREDIT", new BigDecimal("100.00"));
    }

    @Test
    void shouldReturnTheOriginalOutcomeWhenIdempotencyKeyIsReplayed() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-replay-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-replay-target", "0.00");
        var idempotencyKey = nextIdempotencyKey();
        var request = transferOf(sourceAccountId, targetAccountId, "25.00");
        var original = ledgerApi.postTransfer(idempotencyKey, request, TransferResponse.class);

        // when
        var replay = ledgerApi.postTransfer(idempotencyKey, request, TransferResponse.class);

        // then
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getHeaders().getFirst(IDEMPOTENCY_REPLAYED_HEADER)).isEqualTo("true");
        assertThat(transferIdOf(replay)).isEqualTo(transferIdOf(original));
    }

    @Test
    void shouldApplyTheTransferOnceWhenIdempotencyKeyIsReplayed() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-once-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-once-target", "0.00");
        var idempotencyKey = nextIdempotencyKey();
        var request = transferOf(sourceAccountId, targetAccountId, "25.00");

        // when
        ledgerApi.postTransfer(idempotencyKey, request, TransferResponse.class);
        ledgerApi.postTransfer(idempotencyKey, request, TransferResponse.class);

        // then
        assertThat(ledgerApi.balanceOf(sourceAccountId)).isEqualByComparingTo("75.00");
    }

    @Test
    void shouldReturnConflictWhenIdempotencyKeyIsReusedWithADifferentPayload() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-reuse-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-reuse-target", "0.00");
        var idempotencyKey = nextIdempotencyKey();
        ledgerApi.postTransfer(idempotencyKey, transferOf(sourceAccountId, targetAccountId, "25.00"),
                TransferResponse.class);

        // when
        var conflict = ledgerApi.postTransfer(idempotencyKey,
                transferOf(sourceAccountId, targetAccountId, "26.00"), ApiError.class);

        // then
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCodeOf(conflict)).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void shouldRejectTheTransferWhenSourceAccountHasInsufficientFunds() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-poor-source", "10.00");
        var targetAccountId = ledgerApi.openAccount("customer-poor-target", "0.00");

        // when
        var rejection = ledgerApi.postTransfer(nextIdempotencyKey(),
                transferOf(sourceAccountId, targetAccountId, "25.00"), ApiError.class);

        // then
        assertThat(rejection.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(errorCodeOf(rejection)).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void shouldLeaveBalancesUntouchedWhenTransferIsRejected() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-inert-source", "10.00");
        var targetAccountId = ledgerApi.openAccount("customer-inert-target", "0.00");

        // when
        ledgerApi.postTransfer(nextIdempotencyKey(), transferOf(sourceAccountId, targetAccountId, "25.00"),
                ApiError.class);

        // then
        assertThat(ledgerApi.balanceOf(sourceAccountId)).isEqualByComparingTo("10.00");
        assertThat(ledgerApi.balanceOf(targetAccountId)).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldRejectTheTransferWhenSourceAndTargetAreIdentical() {
        // given
        var accountId = ledgerApi.openAccount("customer-self", "100.00");

        // when
        var rejection = ledgerApi.postTransfer(nextIdempotencyKey(), transferOf(accountId, accountId, "25.00"),
                ApiError.class);

        // then
        assertThat(rejection.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(errorCodeOf(rejection)).isEqualTo(ErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void shouldReturnNotFoundWhenTransferReferencesAnUnknownAccount() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-known-source", "100.00");

        // when
        var rejection = ledgerApi.postTransfer(nextIdempotencyKey(),
                transferOf(sourceAccountId, UNKNOWN_ACCOUNT_ID, "25.00"), ApiError.class);

        // then
        assertThat(rejection.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCodeOf(rejection)).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void shouldReturnNotFoundWhenBalanceIsRequestedForAnUnknownAccount() {
        // when
        var failure = ledgerApi.readBalanceFailure(UNKNOWN_ACCOUNT_ID);

        // then
        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errorCodeOf(failure)).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyHeaderIsAbsent() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-keyless-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-keyless-target", "0.00");

        // when
        var failure = ledgerApi.postTransferWithoutIdempotencyKey(
                transferOf(sourceAccountId, targetAccountId, "25.00"), ApiError.class);

        // then
        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errorCodeOf(failure)).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private String postedTransferId(String sourceAccountId, String targetAccountId, String amount) {
        return transferIdOf(ledgerApi.postTransfer(nextIdempotencyKey(),
                transferOf(sourceAccountId, targetAccountId, amount), TransferResponse.class));
    }

    private static String transferIdOf(ResponseEntity<TransferResponse> response) {
        return Objects.requireNonNull(response.getBody()).transferId();
    }

    private static ErrorCode errorCodeOf(ResponseEntity<ApiError> response) {
        return Objects.requireNonNull(response.getBody()).errorCode();
    }

    private static List<EntryResponse> entriesOfTransfer(List<EntryResponse> entries, String transferId) {
        return entries.stream().filter(entry -> entry.transferId().equals(transferId)).toList();
    }
}
