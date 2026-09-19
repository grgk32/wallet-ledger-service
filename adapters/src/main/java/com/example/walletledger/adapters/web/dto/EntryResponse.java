package com.example.walletledger.adapters.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "EntryResponse", description = "One leg of a double-entry posting")
public record EntryResponse(@Schema(example = "2b9f4c1e-8d37-4a52-9c60-5e1a7f3b2d84:debit") String entryId,
                            @Schema(example = "2b9f4c1e-8d37-4a52-9c60-5e1a7f3b2d84") String transferId,
                            @Schema(example = "0c6b2a4e-9f1d-4a6b-8c3e-7d5f2b1a0e94") String accountId,
                            @Schema(example = "DEBIT") String direction,
                            @Schema(example = "25.00") BigDecimal amount,
                            @Schema(example = "EUR") String currency,
                            Instant postedAt) {
}
