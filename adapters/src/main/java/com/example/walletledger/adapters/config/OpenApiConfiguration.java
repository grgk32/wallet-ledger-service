package com.example.walletledger.adapters.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfiguration {

    private static final String API_TITLE = "Wallet Ledger Service";
    private static final String API_VERSION = "1.0.0";
    private static final String API_DESCRIPTION = """
            Double-entry ledger with per-account locking, idempotent transfers and in-memory state.
            A transfer debits one account and credits another atomically, or changes nothing.
            The transfer endpoint requires an Idempotency-Key header and applies each key at most once.""";

    @Bean
    public OpenAPI walletLedgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title(API_TITLE)
                        .version(API_VERSION)
                        .description(API_DESCRIPTION)
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local runtime")));
    }
}
