package com.example.walletledger.web;

import com.example.walletledger.adapters.config.CorsProperties;
import com.example.walletledger.adapters.web.GlobalExceptionHandler;
import com.example.walletledger.adapters.web.TransferController;
import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.ErrorCode;
import com.example.walletledger.adapters.web.dto.TransferRequest;
import com.example.walletledger.adapters.web.mapper.TransferWebMapperImpl;
import com.example.walletledger.adapters.web.mapper.WebTypeConverters;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.api.TransferMoneyApiPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(TransferController.class)
@Import({TransferWebMapperImpl.class, WebTypeConverters.class})
@EnableConfigurationProperties(CorsProperties.class)
class GlobalExceptionHandlerTest {

    private static final String TRANSFERS_PATH = "/api/v1/transfers";
    private static final String UNMAPPED_PATH = "/api/v1/there-is-no-such-resource";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENCY_KEY = "6f1c9d2e-7a83-4b15-9c40-2d8e5f7a1b63";
    private static final String MALFORMED_BODY = "{\"sourceAccountId\": ";
    private static final String STACK_FRAME_MARKER = "\tat ";
    private static final String PARAMETER_NAME = "accountId";

    private static final AccountId SOURCE_ACCOUNT_ID = AccountId.of("0c6b2a4e-9f1d-4a6b-8c3e-7d5f2b1a0e94");
    private static final AccountId TARGET_ACCOUNT_ID = AccountId.of("b71f5d83-2e64-4c19-9a07-1f3d8e5c4b62");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @MockBean
    private TransferMoneyApiPort transferMoney;

    @Test
    void shouldReportValidationFailureWhenBodyCannotBeParsed() throws Exception {
        // when
        var response = mockMvc.perform(post(TRANSFERS_PATH)
                        .header(IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MALFORMED_BODY))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(readApiError(response).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void shouldReportValidationFailureWhenAmountExceedsTheCurrencyPrecision() throws Exception {
        // when
        var response = performTransfer(new TransferRequest(SOURCE_ACCOUNT_ID.value(), TARGET_ACCOUNT_ID.value(),
                new BigDecimal("25.123"), "EUR"));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(readApiError(response).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void shouldReportMethodNotAllowedWhenVerbIsNotSupported() throws Exception {
        // when
        var response = mockMvc.perform(get(TRANSFERS_PATH)).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(readApiError(response).errorCode()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void shouldReportUnsupportedMediaTypeWhenContentTypeIsNotJson() throws Exception {
        // when
        var response = mockMvc.perform(post(TRANSFERS_PATH)
                        .header(IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("source,target,25.00"))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(readApiError(response).errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void shouldReportResourceNotFoundWhenPathIsNotMapped() throws Exception {
        // when
        var response = mockMvc.perform(get(UNMAPPED_PATH)).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(readApiError(response).errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void shouldReportInternalErrorWhenFailureIsUnexpected() throws Exception {
        // given
        given(transferMoney.transfer(any())).willThrow(new IllegalStateException("the ledger store is unreachable"));

        // when
        var response = performTransfer(validTransferRequest());

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(readApiError(response).errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void shouldHideTheStackTraceWhenFailureIsUnexpected() throws Exception {
        // given
        given(transferMoney.transfer(any())).willThrow(new IllegalStateException("the ledger store is unreachable"));

        // when
        var response = performTransfer(validTransferRequest());

        // then
        assertThat(response.getContentAsString())
                .doesNotContain(STACK_FRAME_MARKER)
                .doesNotContain(IllegalStateException.class.getName())
                .doesNotContain("the ledger store is unreachable");
    }

    @Test
    void shouldCarryTheCorrelationIdentifierAndTimestampWhenErrorIsReturned() throws Exception {
        // when
        var response = mockMvc.perform(get(UNMAPPED_PATH)).andReturn().getResponse();

        // then
        assertThat(readApiError(response))
                .satisfies(apiError -> assertThat(apiError.correlationId()).isNotBlank())
                .satisfies(apiError -> assertThat(apiError.timestamp()).isBeforeOrEqualTo(Instant.now()))
                .satisfies(apiError -> assertThat(apiError.details()).isEmpty());
    }

    @Test
    void shouldReportValidationFailureWhenParameterTypeDoesNotMatch() {
        // given
        var typeMismatch = new MethodArgumentTypeMismatchException("not-a-number", Integer.class, PARAMETER_NAME,
                null, new IllegalArgumentException("conversion failed"));

        // when
        var apiError = globalExceptionHandler.onTypeMismatch(typeMismatch);

        // then
        assertThat(apiError.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(apiError.message()).contains(PARAMETER_NAME);
    }

    private MockHttpServletResponse performTransfer(TransferRequest request) throws Exception {
        return mockMvc.perform(post(TRANSFERS_PATH)
                        .header(IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse();
    }

    private ApiError readApiError(MockHttpServletResponse response) throws Exception {
        return objectMapper.readValue(response.getContentAsString(), ApiError.class);
    }

    private static TransferRequest validTransferRequest() {
        return new TransferRequest(SOURCE_ACCOUNT_ID.value(), TARGET_ACCOUNT_ID.value(), new BigDecimal("25.00"),
                "EUR");
    }
}
