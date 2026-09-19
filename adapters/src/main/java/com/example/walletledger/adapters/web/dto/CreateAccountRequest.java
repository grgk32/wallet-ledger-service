package com.example.walletledger.adapters.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreateAccountRequest")
public record CreateAccountRequest(
        @Schema(example = "customer-4711", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128) String ownerReference,

        @Schema(example = "EUR", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a three letter ISO 4217 code") String currency,

        @Schema(example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 4) BigDecimal initialBalance) {
}
