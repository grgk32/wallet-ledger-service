package com.example.walletledger.web;

import com.example.walletledger.adapters.config.CorsProperties;
import com.example.walletledger.adapters.web.TransferController;
import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.ErrorCode;
import com.example.walletledger.adapters.web.dto.TransferRequest;
import com.example.walletledger.adapters.web.dto.TransferResponse;
import com.example.walletledger.adapters.web.mapper.TransferWebMapperImpl;
import com.example.walletledger.adapters.web.mapper.WebTypeConverters;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.RejectionReason;
import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.usecases.api.TransferCommand;
import com.example.walletledger.usecases.api.TransferMoneyApiPort;
import com.example.walletledger.usecases.api.TransferReceipt;
import com.example.walletledger.usecases.exception.IdempotencyKeyReusedException;
import com.example.walletledger.usecases.exception.TransferInProgressException;
import com.example.walletledger.usecases.exception.TransferRejectedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(TransferController.class)
@Import({TransferWebMapperImpl.class, WebTypeConverters.class})
@EnableConfigurationProperties(CorsProperties.class)
class TransferControllerTest {

    private static final String TRANSFERS_PATH = "/api/v1/transfers";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final AccountId SOURCE_ACCOUNT_ID = AccountId.of("0c6b2a4e-9f1d-4a6b-8c3e-7d5f2b1a0e94");
    private static final AccountId TARGET_ACCOUNT_ID = AccountId.of("b71f5d83-2e64-4c19-9a07-1f3d8e5c4b62");
    private static final TransferId TRANSFER_ID = TransferId.of("2b9f4c1e-8d37-4a52-9c60-5e1a7f3b2d84");
    private static final IdempotencyKey IDEMPOTENCY_KEY = IdempotencyKey.of("6f1c9d2e-7a83-4b15-9c40-2d8e5f7a1b63");
    private static final String OVER_LENGTH_IDEMPOTENCY_KEY = "k".repeat(129);
    private static final Instant POSTED_AT = Instant.parse("2026-09-19T10:15:30Z");
    private static final Duration WAIT_BUDGET = Duration.ofSeconds(2);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransferMoneyApiPort transferMoney;

    @Test
    void shouldReturnCreatedWithoutTheReplayFlagWhenTransferIsApplied() throws Exception {
        // given
        given(transferMoney.transfer(any())).willReturn(receiptOf(false));

        // when
        var response = performTransfer(IDEMPOTENCY_KEY.value(), transferRequestOf("25.00"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.getHeader(IDEMPOTENCY_REPLAYED_HEADER)).isEqualTo("false");
        assertThat(readBody(response, TransferResponse.class)).isEqualTo(new TransferResponse(TRANSFER_ID.value(),
                "APPLIED", SOURCE_ACCOUNT_ID.value(), TARGET_ACCOUNT_ID.value(), new BigDecimal("25.00"), "EUR",
                POSTED_AT, false));
    }

    @Test
    void shouldPassTheCommandToTheApiPortWhenTransferIsPosted() throws Exception {
        // given
        given(transferMoney.transfer(any())).willReturn(receiptOf(false));

        // when
        performTransfer(IDEMPOTENCY_KEY.value(), transferRequestOf("25.00"));

        // then
        then(transferMoney).should().transfer(new TransferCommand(IDEMPOTENCY_KEY, SOURCE_ACCOUNT_ID,
                TARGET_ACCOUNT_ID, Money.of(new BigDecimal("25.00"), EURO)));
    }

    @Test
    void shouldReturnTheOriginalTransferWhenKeyIsReplayed() throws Exception {
        // given
        given(transferMoney.transfer(any())).willReturn(receiptOf(true));

        // when
        var response = performTransfer(IDEMPOTENCY_KEY.value(), transferRequestOf("25.00"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.getHeader(IDEMPOTENCY_REPLAYED_HEADER)).isEqualTo("true");
        assertThat(readBody(response, TransferResponse.class))
                .extracting(TransferResponse::transferId, TransferResponse::replayed)
                .containsExactly(TRANSFER_ID.value(), true);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyHeaderIsAbsent() throws Exception {
        // when
        var response = mockMvc.perform(post(TRANSFERS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequestOf("25.00"))))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void shouldReturnBadRequestWhenIdempotencyKeyExceedsTheMaximumLength() throws Exception {
        // when
        var response = performTransfer(OVER_LENGTH_IDEMPOTENCY_KEY, transferRequestOf("25.00"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @ParameterizedTest(name = "{index}: amount {0}")
    @ValueSource(strings = {"0.00", "-0.01", "-25.00"})
    void shouldReturnBadRequestWhenAmountIsNotStrictlyPositive(String amount) throws Exception {
        // when
        var response = performTransfer(IDEMPOTENCY_KEY.value(), transferRequestOf(amount));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidTransferRequests")
    void shouldReturnBadRequestWhenTransferPayloadViolatesAConstraint(String description,
                                                                      TransferRequest request) throws Exception {
        // when
        var response = performTransfer(IDEMPOTENCY_KEY.value(), request);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("missingAccountRejections")
    void shouldReturnNotFoundWhenTransferReferencesAnUnknownAccount(RejectionReason reason) throws Exception {
        // given
        given(transferMoney.transfer(any())).willThrow(new TransferRejectedException(reason, "account is unknown"));

        // when
        var response = performTransfer(IDEMPOTENCY_KEY.value(), transferRequestOf("25.00"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @ParameterizedTest(name = "{index}: {0} -> {1}")
    @MethodSource("ledgerInvariantRejections")
    void shouldReturnUnprocessableEntityWhenLedgerInvariantIsViolated(RejectionReason reason,
                                                                      ErrorCode expectedErrorCode) throws Exception {
        // given
        given(transferMoney.transfer(any())).willThrow(new TransferRejectedException(reason, "invariant violated"));

        // when
        var response = performTransfer(IDEMPOTENCY_KEY.value(), transferRequestOf("25.00"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(expectedErrorCode);
    }

    @Test
    void shouldReturnConflictWhenIdempotencyKeyIsReusedWithADifferentPayload() throws Exception {
        // given
        given(transferMoney.transfer(any())).willThrow(new IdempotencyKeyReusedException(IDEMPOTENCY_KEY));

        // when
        var response = performTransfer(IDEMPOTENCY_KEY.value(), transferRequestOf("25.00"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void shouldReturnConflictWithRetryAfterWhenOriginalRequestIsStillRunning() throws Exception {
        // given
        given(transferMoney.transfer(any()))
                .willThrow(new TransferInProgressException(IDEMPOTENCY_KEY, WAIT_BUDGET));

        // when
        var response = performTransfer(IDEMPOTENCY_KEY.value(), transferRequestOf("25.00"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.TRANSFER_IN_PROGRESS);
    }

    private static Stream<Arguments> invalidTransferRequests() {
        return Stream.of(
                Arguments.of("null source account",
                        new TransferRequest(null, TARGET_ACCOUNT_ID.value(), new BigDecimal("25.00"), "EUR")),
                Arguments.of("blank source account",
                        new TransferRequest("   ", TARGET_ACCOUNT_ID.value(), new BigDecimal("25.00"), "EUR")),
                Arguments.of("null target account",
                        new TransferRequest(SOURCE_ACCOUNT_ID.value(), null, new BigDecimal("25.00"), "EUR")),
                Arguments.of("over length target account",
                        new TransferRequest(SOURCE_ACCOUNT_ID.value(), "t".repeat(65), new BigDecimal("25.00"), "EUR")),
                Arguments.of("null amount",
                        new TransferRequest(SOURCE_ACCOUNT_ID.value(), TARGET_ACCOUNT_ID.value(), null, "EUR")),
                Arguments.of("null currency",
                        new TransferRequest(SOURCE_ACCOUNT_ID.value(), TARGET_ACCOUNT_ID.value(),
                                new BigDecimal("25.00"), null)),
                Arguments.of("two letter currency code",
                        new TransferRequest(SOURCE_ACCOUNT_ID.value(), TARGET_ACCOUNT_ID.value(),
                                new BigDecimal("25.00"), "EU")));
    }

    private static Stream<Arguments> missingAccountRejections() {
        return Stream.of(
                Arguments.of(RejectionReason.SOURCE_ACCOUNT_NOT_FOUND),
                Arguments.of(RejectionReason.TARGET_ACCOUNT_NOT_FOUND));
    }

    private static Stream<Arguments> ledgerInvariantRejections() {
        return Stream.of(
                Arguments.of(RejectionReason.SAME_ACCOUNT_TRANSFER, ErrorCode.SAME_ACCOUNT_TRANSFER),
                Arguments.of(RejectionReason.CURRENCY_MISMATCH, ErrorCode.CURRENCY_MISMATCH),
                Arguments.of(RejectionReason.INSUFFICIENT_FUNDS, ErrorCode.INSUFFICIENT_FUNDS));
    }

    private MockHttpServletResponse performTransfer(String idempotencyKey, TransferRequest request) throws Exception {
        return mockMvc.perform(post(TRANSFERS_PATH)
                        .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse();
    }

    private <T> T readBody(MockHttpServletResponse response, Class<T> responseType) throws Exception {
        return objectMapper.readValue(response.getContentAsString(), responseType);
    }

    private static TransferRequest transferRequestOf(String amount) {
        return new TransferRequest(SOURCE_ACCOUNT_ID.value(), TARGET_ACCOUNT_ID.value(), new BigDecimal(amount), "EUR");
    }

    private static TransferReceipt receiptOf(boolean replayed) {
        return new TransferReceipt(TRANSFER_ID, SOURCE_ACCOUNT_ID, TARGET_ACCOUNT_ID,
                Money.of(new BigDecimal("25.00"), EURO), POSTED_AT, replayed);
    }
}
