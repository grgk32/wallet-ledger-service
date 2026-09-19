package com.example.walletledger.adapters.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "TransferResponse")
public record TransferResponse(@Schema(example = "2b9f4c1e-8d37-4a52-9c60-5e1a7f3b2d84") String transferId,
                               @Schema(example = "APPLIED") String status,
                               @Schema(example = "0c6b2a4e-9f1d-4a6b-8c3e-7d5f2b1a0e94") String sourceAccountId,
                               @Schema(example = "b71f5d83-2e64-4c19-9a07-1f3d8e5c4b62") String targetAccountId,
                               @Schema(example = "25.00") BigDecimal amount,
                               @Schema(example = "EUR") String currency,
                               Instant postedAt,
                               @Schema(description = "True when the response replays an earlier request with the same idempotency key")
                               boolean replayed) {
}
