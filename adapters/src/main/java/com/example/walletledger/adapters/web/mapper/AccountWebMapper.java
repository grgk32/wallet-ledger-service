package com.example.walletledger.adapters.web.mapper;

import com.example.walletledger.adapters.web.dto.AccountResponse;
import com.example.walletledger.adapters.web.dto.BalanceResponse;
import com.example.walletledger.adapters.web.dto.CreateAccountRequest;
import com.example.walletledger.adapters.web.dto.EntryResponse;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.usecases.api.AccountSnapshot;
import com.example.walletledger.usecases.api.CreateAccountCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = WebTypeConverters.class)
public interface AccountWebMapper {

    CreateAccountCommand toCommand(CreateAccountRequest request);

    @Mapping(target = "currency", source = "balance.currency")
    @Mapping(target = "balance", source = "balance.amount")
    AccountResponse toAccountResponse(AccountSnapshot snapshot);

    @Mapping(target = "currency", source = "balance.currency")
    @Mapping(target = "balance", source = "balance.amount")
    BalanceResponse toBalanceResponse(AccountSnapshot snapshot);

    @Mapping(target = "currency", source = "amount.currency")
    @Mapping(target = "amount", source = "amount.amount")
    EntryResponse toEntryResponse(LedgerEntry entry);

    List<EntryResponse> toEntryResponses(List<LedgerEntry> entries);
}
