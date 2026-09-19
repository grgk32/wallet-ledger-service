package com.example.walletledger.adapters.web.mapper;

import com.example.walletledger.adapters.web.dto.TransferRequest;
import com.example.walletledger.adapters.web.dto.TransferResponse;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.usecases.api.TransferCommand;
import com.example.walletledger.usecases.api.TransferReceipt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = WebTypeConverters.class)
public interface TransferWebMapper {

    @Mapping(target = "idempotencyKey", source = "idempotencyKey")
    @Mapping(target = "sourceAccountId", source = "request.sourceAccountId")
    @Mapping(target = "targetAccountId", source = "request.targetAccountId")
    @Mapping(target = "amount", source = "request")
    TransferCommand toCommand(TransferRequest request, String idempotencyKey);

    @Mapping(target = "status", constant = "APPLIED")
    @Mapping(target = "currency", source = "amount.currency")
    @Mapping(target = "amount", source = "amount.amount")
    TransferResponse toResponse(TransferReceipt receipt);

    default Money toMoney(TransferRequest request) {
        return Money.of(request.amount(), Money.currencyOf(request.currency()));
    }
}
