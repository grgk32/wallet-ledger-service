package com.example.walletledger.adapters.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "TransferRequest")
public record TransferRequest(
        @Schema(example = "0c6b2a4e-9f1d-4a6b-8c3e-7d5f2b1a0e94", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 64) String sourceAccountId,

        @Schema(example = "b71f5d83-2e64-4c19-9a07-1f3d8e5c4b62", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 64) String targetAccountId,

        @Schema(example = "25.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 4) BigDecimal amount,

        @Schema(example = "EUR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a three letter ISO 4217 code") String currency) {
}
