package com.example.walletledger.integration;

import com.example.walletledger.adapters.web.dto.AccountResponse;
import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.BalanceResponse;
import com.example.walletledger.adapters.web.dto.CreateAccountRequest;
import com.example.walletledger.adapters.web.dto.EntryResponse;
import com.example.walletledger.adapters.web.dto.TransferRequest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class LedgerApiClient {

    static final String ACCOUNTS_PATH = "/api/v1/accounts";
    static final String BALANCE_PATH = "/api/v1/accounts/{accountId}/balance";
    static final String ENTRIES_PATH = "/api/v1/accounts/{accountId}/entries";
    static final String TRANSFERS_PATH = "/api/v1/transfers";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";
    static final String EURO_CODE = "EUR";

    private static final ParameterizedTypeReference<List<EntryResponse>> ENTRY_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final TestRestTemplate restTemplate;

    LedgerApiClient(TestRestTemplate restTemplate) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
    }

    static String nextIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    static TransferRequest transferOf(String sourceAccountId, String targetAccountId, String amount) {
        return new TransferRequest(sourceAccountId, targetAccountId, new BigDecimal(amount), EURO_CODE);
    }

    ResponseEntity<AccountResponse> createAccount(String ownerReference, String initialBalance) {
        return restTemplate.postForEntity(ACCOUNTS_PATH,
                new CreateAccountRequest(ownerReference, EURO_CODE, new BigDecimal(initialBalance)),
                AccountResponse.class);
    }

    String openAccount(String ownerReference, String initialBalance) {
        return Objects.requireNonNull(createAccount(ownerReference, initialBalance).getBody()).accountId();
    }

    ResponseEntity<BalanceResponse> readBalance(String accountId) {
        return restTemplate.getForEntity(BALANCE_PATH, BalanceResponse.class, accountId);
    }

    BigDecimal balanceOf(String accountId) {
        return Objects.requireNonNull(readBalance(accountId).getBody()).balance();
    }

    ResponseEntity<ApiError> readBalanceFailure(String accountId) {
        return restTemplate.getForEntity(BALANCE_PATH, ApiError.class, accountId);
    }

    List<EntryResponse> readEntries(String accountId) {
        return Objects.requireNonNull(
                restTemplate.exchange(ENTRIES_PATH, HttpMethod.GET, HttpEntity.EMPTY, ENTRY_LIST, accountId)
                        .getBody());
    }

    <T> ResponseEntity<T> postTransfer(String idempotencyKey, TransferRequest request, Class<T> responseType) {
        return restTemplate.exchange(TRANSFERS_PATH, HttpMethod.POST,
                new HttpEntity<>(request, transferHeaders(idempotencyKey)), responseType);
    }

    <T> ResponseEntity<T> postTransferWithoutIdempotencyKey(TransferRequest request, Class<T> responseType) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(TRANSFERS_PATH, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);
    }

    private HttpHeaders transferHeaders(String idempotencyKey) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        return headers;
    }
}
