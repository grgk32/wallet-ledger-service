package com.example.walletledger.adapters.web;

import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.TransferRequest;
import com.example.walletledger.adapters.web.dto.TransferResponse;
import com.example.walletledger.adapters.web.mapper.TransferWebMapper;
import com.example.walletledger.usecases.api.TransferMoneyApiPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Idempotent double-entry transfers between two accounts")
public class TransferController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final int MAXIMUM_IDEMPOTENCY_KEY_LENGTH = 128;

    private final TransferMoneyApiPort transferMoney;
    private final TransferWebMapper transferWebMapper;

    public TransferController(TransferMoneyApiPort transferMoney, TransferWebMapper transferWebMapper) {
        this.transferMoney = Objects.requireNonNull(transferMoney, "transferMoney");
        this.transferWebMapper = Objects.requireNonNull(transferWebMapper, "transferWebMapper");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a transfer",
            description = "Debits the source account and credits the target account atomically. "
                    + "A repeated Idempotency-Key returns the original outcome and applies the transfer once.")
    @ApiResponse(responseCode = "201", description = "Transfer applied, or replayed from an earlier request")
    @ApiResponse(responseCode = "400", description = "Request payload or idempotency key rejected",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Source or target account does not exist",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Key reused with a different payload, or original still running",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Transfer rejected by a ledger invariant",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TransferResponse postTransfer(
            @Parameter(description = "Client generated key that makes the request retryable", required = true,
                    example = "6f1c9d2e-7a83-4b15-9c40-2d8e5f7a1b63")
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank @Size(max = MAXIMUM_IDEMPOTENCY_KEY_LENGTH)
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            HttpServletResponse response) {
        var receipt = transferMoney.transfer(transferWebMapper.toCommand(request, idempotencyKey));
        response.setHeader(IDEMPOTENCY_REPLAYED_HEADER, Boolean.toString(receipt.replayed()));
        return transferWebMapper.toResponse(receipt);
    }
}
