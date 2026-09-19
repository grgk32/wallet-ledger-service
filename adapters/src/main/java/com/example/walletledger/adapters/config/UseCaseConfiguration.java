package com.example.walletledger.adapters.config;

import com.example.walletledger.adapters.metrics.TransferMetricsDecorator;
import com.example.walletledger.usecases.api.CreateAccountApiPort;
import com.example.walletledger.usecases.api.GetAccountBalanceApiPort;
import com.example.walletledger.usecases.api.GetAccountEntriesApiPort;
import com.example.walletledger.usecases.api.TransferMoneyApiPort;
import com.example.walletledger.usecases.service.CreateAccountService;
import com.example.walletledger.usecases.service.GetAccountBalanceService;
import com.example.walletledger.usecases.service.GetAccountEntriesService;
import com.example.walletledger.usecases.service.TransferMoneyService;
import com.example.walletledger.usecases.spi.AccountIdGenerator;
import com.example.walletledger.usecases.spi.AccountSpiPort;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.IdempotencyRecordSpiPort;
import com.example.walletledger.usecases.spi.LedgerJournalSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.TransferIdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {

    @Bean
    public CreateAccountApiPort createAccountService(LedgerTransactionSpiPort ledgerTransactions,
                                                     AccountIdGenerator accountIdGenerator,
                                                     TransferIdGenerator transferIdGenerator,
                                                     ClockSpiPort clock) {
        return new CreateAccountService(ledgerTransactions, accountIdGenerator, transferIdGenerator, clock);
    }

    @Bean
    public GetAccountBalanceApiPort getAccountBalanceService(LedgerTransactionSpiPort ledgerTransactions,
                                                             ClockSpiPort clock) {
        return new GetAccountBalanceService(ledgerTransactions, clock);
    }

    @Bean
    public GetAccountEntriesApiPort getAccountEntriesService(AccountSpiPort accounts,
                                                             LedgerJournalSpiPort ledgerJournal) {
        return new GetAccountEntriesService(accounts, ledgerJournal);
    }

    @Bean
    public TransferMoneyApiPort transferMoneyApiPort(LedgerTransactionSpiPort ledgerTransactions,
                                                     IdempotencyRecordSpiPort idempotencyRecords,
                                                     TransferIdGenerator transferIdGenerator,
                                                     ClockSpiPort clock,
                                                     LedgerProperties ledgerProperties,
                                                     MeterRegistry meterRegistry) {
        var transferMoneyService = new TransferMoneyService(ledgerTransactions, idempotencyRecords,
                transferIdGenerator, clock, ledgerProperties.inFlightWaitBudget());
        return new TransferMetricsDecorator(transferMoneyService, meterRegistry);
    }
}
