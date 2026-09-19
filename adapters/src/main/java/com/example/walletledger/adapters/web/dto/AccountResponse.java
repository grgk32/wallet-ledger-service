package com.example.walletledger.adapters.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "AccountResponse")
public record AccountResponse(@Schema(example = "0c6b2a4e-9f1d-4a6b-8c3e-7d5f2b1a0e94") String accountId,
                              @Schema(example = "customer-4711") String ownerReference,
                              @Schema(example = "EUR") String currency,
                              @Schema(example = "100.00") BigDecimal balance) {
}
