package com.example.walletledger.adapters.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "ApiError", description = "Standard error payload returned by every failing endpoint")
public record ApiError(@Schema(example = "INSUFFICIENT_FUNDS") ErrorCode errorCode,
                       @Schema(example = "account 8f1c holds 10.00 EUR and cannot release 25.00 EUR") String message,
                       @Schema(description = "Field level validation messages") List<String> details,
                       @Schema(example = "0f2b7d9a4c8e4f1b9d3a6c5e2b7f8a10") String correlationId,
                       Instant timestamp) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static ApiError of(ErrorCode errorCode, String message, String correlationId, Instant timestamp) {
        return new ApiError(errorCode, message, List.of(), correlationId, timestamp);
    }
}
