package com.example.walletledger.adapters.web.mapper;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.core.model.TransferId;
import org.springframework.stereotype.Component;

import java.util.Currency;

@Component
public class WebTypeConverters {

    public AccountId toAccountId(String accountId) {
        return AccountId.of(accountId);
    }

    public String fromAccountId(AccountId accountId) {
        return accountId.value();
    }

    public String fromTransferId(TransferId transferId) {
        return transferId.value();
    }

    public IdempotencyKey toIdempotencyKey(String idempotencyKey) {
        return IdempotencyKey.of(idempotencyKey);
    }

    public Currency toCurrency(String currencyCode) {
        return Money.currencyOf(currencyCode);
    }

    public String fromCurrency(Currency currency) {
        return currency.getCurrencyCode();
    }
}
