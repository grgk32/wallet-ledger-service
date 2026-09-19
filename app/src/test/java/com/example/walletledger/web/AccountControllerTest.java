package com.example.walletledger.web;

import com.example.walletledger.adapters.config.CorsProperties;
import com.example.walletledger.adapters.web.AccountController;
import com.example.walletledger.adapters.web.dto.AccountResponse;
import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.BalanceResponse;
import com.example.walletledger.adapters.web.dto.CreateAccountRequest;
import com.example.walletledger.adapters.web.dto.EntryResponse;
import com.example.walletledger.adapters.web.dto.ErrorCode;
import com.example.walletledger.adapters.web.mapper.AccountWebMapperImpl;
import com.example.walletledger.adapters.web.mapper.WebTypeConverters;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.EntryDirection;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import com.example.walletledger.usecases.api.AccountSnapshot;
import com.example.walletledger.usecases.api.CreateAccountApiPort;
import com.example.walletledger.usecases.api.CreateAccountCommand;
import com.example.walletledger.usecases.api.GetAccountBalanceApiPort;
import com.example.walletledger.usecases.api.GetAccountEntriesApiPort;
import com.example.walletledger.usecases.exception.AccountNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(AccountController.class)
@Import({AccountWebMapperImpl.class, WebTypeConverters.class})
@EnableConfigurationProperties(CorsProperties.class)
class AccountControllerTest {

    private static final String ACCOUNTS_PATH = "/api/v1/accounts";
    private static final String BALANCE_PATH = "/api/v1/accounts/{accountId}/balance";
    private static final String ENTRIES_PATH = "/api/v1/accounts/{accountId}/entries";

    private static final Currency EURO = Currency.getInstance("EUR");
    private static final AccountId ACCOUNT_ID = AccountId.of("0c6b2a4e-9f1d-4a6b-8c3e-7d5f2b1a0e94");
    private static final TransferId TRANSFER_ID = TransferId.of("2b9f4c1e-8d37-4a52-9c60-5e1a7f3b2d84");
    private static final String OWNER_REFERENCE = "customer-4711";
    private static final String OVER_LENGTH_OWNER_REFERENCE = "o".repeat(129);
    private static final Instant OBSERVED_AT = Instant.parse("2026-09-19T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateAccountApiPort createAccount;

    @MockBean
    private GetAccountBalanceApiPort getAccountBalance;

    @MockBean
    private GetAccountEntriesApiPort getAccountEntries;

    @Test
    void shouldReturnCreatedWithTheOpenedAccountWhenPayloadIsValid() throws Exception {
        // given
        given(createAccount.createAccount(any())).willReturn(snapshotOf("100.00"));

        // when
        var response = performCreateAccount(new CreateAccountRequest(OWNER_REFERENCE, "EUR", new BigDecimal("100.00")));

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(readBody(response, AccountResponse.class))
                .isEqualTo(new AccountResponse(ACCOUNT_ID.value(), OWNER_REFERENCE, "EUR", new BigDecimal("100.00")));
    }

    @Test
    void shouldPassTheCommandToTheApiPortWhenAccountIsCreated() throws Exception {
        // given
        given(createAccount.createAccount(any())).willReturn(snapshotOf("100.00"));

        // when
        performCreateAccount(new CreateAccountRequest(OWNER_REFERENCE, "EUR", new BigDecimal("100.00")));

        // then
        then(createAccount).should()
                .createAccount(new CreateAccountCommand(OWNER_REFERENCE, EURO, new BigDecimal("100.00")));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("invalidCreateAccountRequests")
    void shouldReturnBadRequestWhenCreationPayloadViolatesAConstraint(String description,
                                                                      CreateAccountRequest request) throws Exception {
        // when
        var response = performCreateAccount(request);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void shouldReportTheViolatedFieldWhenCreationPayloadIsRejected() throws Exception {
        // when
        var response = performCreateAccount(new CreateAccountRequest("  ", "EUR", new BigDecimal("1.00")));

        // then
        assertThat(readBody(response, ApiError.class).details()).isNotEmpty();
    }

    @Test
    void shouldReturnTheBalanceWhenAccountExists() throws Exception {
        // given
        given(getAccountBalance.getBalance(ACCOUNT_ID)).willReturn(snapshotOf("75.50"));

        // when
        var response = mockMvc.perform(get(BALANCE_PATH, ACCOUNT_ID.value())).andReturn().getResponse();

        // then
        assertThat(readBody(response, BalanceResponse.class))
                .isEqualTo(new BalanceResponse(ACCOUNT_ID.value(), "EUR", new BigDecimal("75.50"), OBSERVED_AT));
    }

    @Test
    void shouldReturnNotFoundWhenBalanceIsRequestedForAnUnknownAccount() throws Exception {
        // given
        given(getAccountBalance.getBalance(ACCOUNT_ID)).willThrow(new AccountNotFoundException(ACCOUNT_ID));

        // when
        var response = mockMvc.perform(get(BALANCE_PATH, ACCOUNT_ID.value())).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void shouldReturnTheJournalWhenEntriesAreRequested() throws Exception {
        // given
        given(getAccountEntries.findEntries(ACCOUNT_ID)).willReturn(List.of(creditEntry()));

        // when
        var response = mockMvc.perform(get(ENTRIES_PATH, ACCOUNT_ID.value())).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(readEntries(response)).containsExactly(new EntryResponse(TRANSFER_ID.value() + ":credit",
                TRANSFER_ID.value(), ACCOUNT_ID.value(), "CREDIT", new BigDecimal("100.00"), "EUR", OBSERVED_AT));
    }

    @Test
    void shouldReturnAnEmptyListWhenAccountHasNoEntries() throws Exception {
        // given
        given(getAccountEntries.findEntries(ACCOUNT_ID)).willReturn(List.of());

        // when
        var response = mockMvc.perform(get(ENTRIES_PATH, ACCOUNT_ID.value())).andReturn().getResponse();

        // then
        assertThat(readEntries(response)).isEmpty();
    }

    @Test
    void shouldReturnNotFoundWhenEntriesAreRequestedForAnUnknownAccount() throws Exception {
        // given
        given(getAccountEntries.findEntries(ACCOUNT_ID)).willThrow(new AccountNotFoundException(ACCOUNT_ID));

        // when
        var response = mockMvc.perform(get(ENTRIES_PATH, ACCOUNT_ID.value())).andReturn().getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(readBody(response, ApiError.class).errorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    private static Stream<Arguments> invalidCreateAccountRequests() {
        return Stream.of(
                Arguments.of("null owner reference",
                        new CreateAccountRequest(null, "EUR", new BigDecimal("1.00"))),
                Arguments.of("blank owner reference",
                        new CreateAccountRequest("   ", "EUR", new BigDecimal("1.00"))),
                Arguments.of("over length owner reference",
                        new CreateAccountRequest(OVER_LENGTH_OWNER_REFERENCE, "EUR", new BigDecimal("1.00"))),
                Arguments.of("null currency",
                        new CreateAccountRequest(OWNER_REFERENCE, null, new BigDecimal("1.00"))),
                Arguments.of("two letter currency code",
                        new CreateAccountRequest(OWNER_REFERENCE, "EU", new BigDecimal("1.00"))),
                Arguments.of("four letter currency code",
                        new CreateAccountRequest(OWNER_REFERENCE, "EURO", new BigDecimal("1.00"))),
                Arguments.of("numeric currency code",
                        new CreateAccountRequest(OWNER_REFERENCE, "978", new BigDecimal("1.00"))),
                Arguments.of("null initial balance",
                        new CreateAccountRequest(OWNER_REFERENCE, "EUR", null)),
                Arguments.of("negative initial balance",
                        new CreateAccountRequest(OWNER_REFERENCE, "EUR", new BigDecimal("-0.01"))));
    }

    private MockHttpServletResponse performCreateAccount(CreateAccountRequest request) throws Exception {
        return mockMvc.perform(post(ACCOUNTS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse();
    }

    private <T> T readBody(MockHttpServletResponse response, Class<T> responseType) throws Exception {
        return objectMapper.readValue(response.getContentAsString(), responseType);
    }

    private List<EntryResponse> readEntries(MockHttpServletResponse response) throws Exception {
        return objectMapper.readValue(response.getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, EntryResponse.class));
    }

    private static AccountSnapshot snapshotOf(String balance) {
        return new AccountSnapshot(ACCOUNT_ID, OWNER_REFERENCE, Money.of(new BigDecimal(balance), EURO), OBSERVED_AT);
    }

    private static LedgerEntry creditEntry() {
        return new LedgerEntry(TRANSFER_ID.value() + ":credit", TRANSFER_ID, ACCOUNT_ID, EntryDirection.CREDIT,
                Money.of(new BigDecimal("100.00"), EURO), OBSERVED_AT);
    }
}
