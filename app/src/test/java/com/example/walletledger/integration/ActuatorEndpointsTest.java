package com.example.walletledger.integration;

import com.example.walletledger.adapters.web.dto.TransferResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;

import static com.example.walletledger.integration.LedgerApiClient.nextIdempotencyKey;
import static com.example.walletledger.integration.LedgerApiClient.transferOf;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ActiveProfiles("test")
class ActuatorEndpointsTest {

    private static final String HEALTH_PATH = "/actuator/health";
    private static final String LIVENESS_PATH = "/actuator/health/liveness";
    private static final String READINESS_PATH = "/actuator/health/readiness";
    private static final String METRICS_PATH = "/actuator/metrics";
    private static final String PROMETHEUS_PATH = "/actuator/prometheus";

    private static final String LEDGER_COMPONENT = "ledger";
    private static final String STATUS_FIELD = "status";
    private static final String UP_STATUS = "UP";
    private static final String ACCOUNT_GAUGE = "wallet.ledger.accounts";
    private static final String TRANSFER_COUNTER = "wallet_ledger_transfers_total";
    private static final String TRANSFER_TIMER = "wallet_ledger_transfer_duration_seconds";
    private static final String ACCOUNT_GAUGE_SCRAPE = "wallet_ledger_accounts";

    @Autowired
    private TestRestTemplate restTemplate;

    private LedgerApiClient ledgerApi;

    @BeforeEach
    void createApiClient() {
        ledgerApi = new LedgerApiClient(restTemplate);
    }

    @Test
    void shouldReportTheLedgerComponentWhenHealthIsRequested() {
        // when
        var health = readJson(HEALTH_PATH);

        // then
        assertThat(health.path(STATUS_FIELD).asText()).isEqualTo(UP_STATUS);
        assertThat(health.path("components").path(LEDGER_COMPONENT).path(STATUS_FIELD).asText()).isEqualTo(UP_STATUS);
    }

    @Test
    void shouldExposeTheStoreSizesWhenLedgerHealthIsRequested() {
        // when
        var details = readJson(HEALTH_PATH).path("components").path(LEDGER_COMPONENT).path("details");

        // then
        assertThat(details.path("accounts").isNumber()).isTrue();
        assertThat(details.path("idempotencyRecords").isNumber()).isTrue();
        assertThat(details.path("lockedAccounts").isNumber()).isTrue();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {LIVENESS_PATH, READINESS_PATH})
    void shouldReportUpWhenKubernetesProbeIsRequested(String probePath) {
        // when
        var probe = restTemplate.getForEntity(probePath, JsonNode.class);

        // then
        assertThat(probe.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(probe.getBody()).path(STATUS_FIELD).asText()).isEqualTo(UP_STATUS);
    }

    @Test
    void shouldListTheBusinessGaugeWhenMetricNamesAreRequested() {
        // when
        var names = readJson(METRICS_PATH).path("names");

        // then
        assertThat(names.toString()).contains(ACCOUNT_GAUGE);
    }

    @Test
    void shouldPublishBusinessMetersWhenTransferHasBeenPosted() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-metrics-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-metrics-target", "0.00");
        ledgerApi.postTransfer(nextIdempotencyKey(), transferOf(sourceAccountId, targetAccountId, "15.00"),
                TransferResponse.class);

        // when
        var scrape = readPrometheusScrape();

        // then
        assertThat(scrape)
                .contains(TRANSFER_COUNTER)
                .contains(TRANSFER_TIMER)
                .contains(ACCOUNT_GAUGE_SCRAPE);
    }

    @Test
    void shouldPublishDurationQuantilesWhenTransferHasBeenPosted() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-quantile-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-quantile-target", "0.00");
        ledgerApi.postTransfer(nextIdempotencyKey(), transferOf(sourceAccountId, targetAccountId, "5.00"),
                TransferResponse.class);

        // when
        var scrape = readPrometheusScrape();

        // then
        assertThat(scrape)
                .contains("quantile=\"0.5\"")
                .contains("quantile=\"0.95\"")
                .contains("quantile=\"0.99\"");
    }

    @Test
    void shouldTagTheOutcomeWhenTransferCounterIsScraped() {
        // given
        var sourceAccountId = ledgerApi.openAccount("customer-outcome-source", "100.00");
        var targetAccountId = ledgerApi.openAccount("customer-outcome-target", "0.00");
        ledgerApi.postTransfer(nextIdempotencyKey(), transferOf(sourceAccountId, targetAccountId, "7.00"),
                TransferResponse.class);

        // when
        var scrape = readPrometheusScrape();

        // then
        assertThat(scrape).contains("outcome=\"applied\"");
    }

    private JsonNode readJson(String path) {
        var response = restTemplate.getForEntity(path, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Objects.requireNonNull(response.getBody());
    }

    private String readPrometheusScrape() {
        var response = restTemplate.getForEntity(PROMETHEUS_PATH, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Objects.requireNonNull(response.getBody());
    }
}
