package com.example.walletledger.integration;

import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "rate-limit.enabled=true",
                "rate-limit.requests-per-period=3",
                "rate-limit.period=1m"})
@ActiveProfiles("test")
class RateLimitFilterTest {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String BALANCE_PATH = "/api/v1/accounts/unknown-account/balance";
    private static final String HEALTH_PATH = "/actuator/health";
    private static final String API_DOCS_PATH = "/v3/api-docs";
    private static final int REQUESTS_PER_PERIOD = 3;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldServeEveryRequestWhenBudgetIsNotExhausted() {
        // given
        var client = "10.0.0.1";

        // when
        var lastPermitted = repeat(BALANCE_PATH, client, REQUESTS_PER_PERIOD);

        // then
        assertThat(lastPermitted.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturnTooManyRequestsWhenBudgetIsExhausted() {
        // given
        var client = "10.0.0.2";
        repeat(BALANCE_PATH, client, REQUESTS_PER_PERIOD);

        // when
        var throttled = call(BALANCE_PATH, client, ApiError.class);

        // then
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Objects.requireNonNull(throttled.getBody()).errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    void shouldAdviseWhenToRetryWhenBudgetIsExhausted() {
        // given
        var client = "10.0.0.3";
        repeat(BALANCE_PATH, client, REQUESTS_PER_PERIOD);

        // when
        var throttled = call(BALANCE_PATH, client, ApiError.class);

        // then
        assertThat(Objects.requireNonNull(throttled.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)))
                .satisfies(retryAfter -> assertThat(Long.parseLong(retryAfter)).isPositive());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {HEALTH_PATH, API_DOCS_PATH})
    void shouldStayReachableWhenPathIsExemptAndBudgetIsExhausted(String exemptPath) {
        // given
        var client = "10.0.0." + exemptPath.hashCode();
        repeat(BALANCE_PATH, client, REQUESTS_PER_PERIOD);
        assertThat(call(BALANCE_PATH, client, ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // when
        var exemptResponse = call(exemptPath, client, String.class);

        // then
        assertThat(exemptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldTrackBudgetsPerClientWhenAnotherClientIsThrottled() {
        // given
        var throttledClient = "10.0.0.4";
        var freshClient = "10.0.0.5";
        repeat(BALANCE_PATH, throttledClient, REQUESTS_PER_PERIOD);

        // when
        var throttled = call(BALANCE_PATH, throttledClient, ApiError.class);
        var permitted = call(BALANCE_PATH, freshClient, ApiError.class);

        // then
        assertThat(throttled.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(permitted.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<ApiError> repeat(String path, String client, int times) {
        ResponseEntity<ApiError> lastResponse = null;
        for (var attempt = 0; attempt < times; attempt++) {
            lastResponse = call(path, client, ApiError.class);
        }
        return Objects.requireNonNull(lastResponse);
    }

    private <T> ResponseEntity<T> call(String path, String client, Class<T> responseType) {
        var headers = new HttpHeaders();
        headers.set(FORWARDED_FOR_HEADER, client);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }
}
