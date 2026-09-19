package com.example.walletledger.adapters.web;

import com.example.walletledger.adapters.web.dto.AccountResponse;
import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.BalanceResponse;
import com.example.walletledger.adapters.web.dto.CreateAccountRequest;
import com.example.walletledger.adapters.web.dto.EntryResponse;
import com.example.walletledger.adapters.web.mapper.AccountWebMapper;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.api.CreateAccountApiPort;
import com.example.walletledger.usecases.api.GetAccountBalanceApiPort;
import com.example.walletledger.usecases.api.GetAccountEntriesApiPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Account creation, balance and journal reads")
public class AccountController {

    private static final int MAXIMUM_ACCOUNT_ID_LENGTH = 64;

    private final CreateAccountApiPort createAccount;
    private final GetAccountBalanceApiPort getAccountBalance;
    private final GetAccountEntriesApiPort getAccountEntries;
    private final AccountWebMapper accountWebMapper;

    public AccountController(CreateAccountApiPort createAccount,
                             GetAccountBalanceApiPort getAccountBalance,
                             GetAccountEntriesApiPort getAccountEntries,
                             AccountWebMapper accountWebMapper) {
        this.createAccount = Objects.requireNonNull(createAccount, "createAccount");
        this.getAccountBalance = Objects.requireNonNull(getAccountBalance, "getAccountBalance");
        this.getAccountEntries = Objects.requireNonNull(getAccountEntries, "getAccountEntries");
        this.accountWebMapper = Objects.requireNonNull(accountWebMapper, "accountWebMapper");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account",
            description = "Opens an account in a single currency with an optional non-negative opening balance")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Request payload rejected",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountWebMapper.toAccountResponse(createAccount.createAccount(accountWebMapper.toCommand(request)));
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Read an account balance",
            description = "Returns the balance observed under the account lock, so the value is never a torn read")
    @ApiResponse(responseCode = "200", description = "Balance returned")
    @ApiResponse(responseCode = "404", description = "Account does not exist",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public BalanceResponse getBalance(
            @PathVariable @NotBlank @Size(max = MAXIMUM_ACCOUNT_ID_LENGTH) String accountId) {
        return accountWebMapper.toBalanceResponse(getAccountBalance.getBalance(AccountId.of(accountId)));
    }

    @GetMapping("/{accountId}/entries")
    @Operation(summary = "Read the journal of an account",
            description = "Returns every posted entry for the account in the order in which the entries committed")
    @ApiResponse(responseCode = "200", description = "Journal returned")
    @ApiResponse(responseCode = "404", description = "Account does not exist",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public List<EntryResponse> getEntries(
            @PathVariable @NotBlank @Size(max = MAXIMUM_ACCOUNT_ID_LENGTH) String accountId) {
        return accountWebMapper.toEntryResponses(getAccountEntries.findEntries(AccountId.of(accountId)));
    }
}
