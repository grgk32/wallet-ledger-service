package com.example.walletledger.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityHeadersTest {

    private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";
    private static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
    private static final String FRAME_OPTIONS_HEADER = "X-Frame-Options";
    private static final String STRICT_TRANSPORT_SECURITY_HEADER = "Strict-Transport-Security";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Autowired
    private TestRestTemplate restTemplate;

    private LedgerApiClient ledgerApi;

    @BeforeEach
    void createApiClient() {
        ledgerApi = new LedgerApiClient(restTemplate);
    }

    @ParameterizedTest(name = "{index}: {0} -> {1}")
    @CsvSource({
            "X-Content-Type-Options,nosniff",
            "X-Frame-Options,DENY",
            "Referrer-Policy,no-referrer",
            "Strict-Transport-Security,max-age=31536000; includeSubDomains"})
    void shouldSetTheSecurityHeaderWhenApiRespondsToAWriteRequest(String headerName, String expectedValue) {
        // when
        var response = ledgerApi.createAccount("customer-security-headers", "1.00");

        // then
        assertThat(response.getHeaders().getFirst(headerName)).isEqualTo(expectedValue);
    }

    @Test
    void shouldRestrictContentSourcesWhenApiRespondsToAWriteRequest() {
        // when
        var response = ledgerApi.createAccount("customer-content-policy", "1.00");

        // then
        assertThat(response.getHeaders().getFirst(CONTENT_SECURITY_POLICY_HEADER))
                .contains("default-src 'self'")
                .contains("frame-ancestors 'none'");
    }

    @Test
    void shouldSetTheSecurityHeadersWhenApiRespondsToAReadRequest() {
        // given
        var accountId = ledgerApi.openAccount("customer-read-headers", "1.00");

        // when
        var response = ledgerApi.readBalance(accountId);

        // then
        assertThat(response.getHeaders().keySet())
                .contains(CONTENT_SECURITY_POLICY_HEADER, CONTENT_TYPE_OPTIONS_HEADER, FRAME_OPTIONS_HEADER,
                        STRICT_TRANSPORT_SECURITY_HEADER);
    }

    @Test
    void shouldReturnACorrelationIdentifierWhenRequestCarriesNone() {
        // when
        var response = ledgerApi.createAccount("customer-correlation", "1.00");

        // then
        assertThat(response.getHeaders().getFirst(CORRELATION_ID_HEADER)).isNotBlank();
    }
}
