package com.example.walletledger.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocumentTest {

    private static final String API_DOCS_PATH = "/v3/api-docs";
    private static final String SWAGGER_UI_PATH = "/swagger-ui.html";
    private static final String SWAGGER_UI_MARKER = "swagger-ui";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int localServerPort;

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {
            "/api/v1/accounts",
            "/api/v1/accounts/{accountId}/balance",
            "/api/v1/accounts/{accountId}/entries",
            "/api/v1/transfers"})
    void shouldDocumentTheOperationWhenApiDocumentIsRequested(String documentedPath) {
        // when
        var paths = readApiDocument().path("paths");

        // then
        assertThat(paths.has(documentedPath)).isTrue();
    }

    @Test
    void shouldDocumentTheErrorPayloadWhenApiDocumentIsRequested() {
        // when
        var schemas = readApiDocument().path("components").path("schemas");

        // then
        assertThat(schemas.has("ApiError")).isTrue();
        assertThat(schemas.path("ApiError").path("properties").has("errorCode")).isTrue();
    }

    @Test
    void shouldCarryTheServiceTitleWhenApiDocumentIsRequested() {
        // when
        var info = readApiDocument().path("info");

        // then
        assertThat(info.path("title").asText()).isEqualTo("Wallet Ledger Service");
        assertThat(info.path("version").asText()).isEqualTo("1.0.0");
    }

    @Test
    void shouldRedirectToTheUserInterfaceWhenSwaggerPathIsRequested() {
        // when
        var response = nonRedirectingRestTemplate().getForEntity(swaggerUserInterfaceUrl(), String.class);

        // then
        assertThat(response.getStatusCode().is3xxRedirection()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).contains(SWAGGER_UI_MARKER);
    }

    private JsonNode readApiDocument() {
        var response = restTemplate.getForEntity(API_DOCS_PATH, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Objects.requireNonNull(response.getBody());
    }

    private String swaggerUserInterfaceUrl() {
        return "http://localhost:" + localServerPort + SWAGGER_UI_PATH;
    }

    private TestRestTemplate nonRedirectingRestTemplate() {
        var redirectAgnosticTemplate = new TestRestTemplate();
        redirectAgnosticTemplate.getRestTemplate().setRequestFactory(new NonRedirectingRequestFactory());
        return redirectAgnosticTemplate;
    }

    private static final class NonRedirectingRequestFactory extends SimpleClientHttpRequestFactory {

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }
}
