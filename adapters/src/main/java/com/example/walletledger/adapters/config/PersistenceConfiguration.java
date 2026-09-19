package com.example.walletledger.adapters.config;

import com.example.walletledger.adapters.persistence.memory.AccountLockRegistry;
import com.example.walletledger.adapters.persistence.memory.CaffeineIdempotencyRecordAdapter;
import com.example.walletledger.adapters.persistence.memory.InMemoryAccountAdapter;
import com.example.walletledger.adapters.persistence.memory.InMemoryLedgerJournalAdapter;
import com.example.walletledger.adapters.persistence.memory.InMemoryLedgerStore;
import com.example.walletledger.adapters.persistence.memory.InMemoryLedgerTransactionAdapter;
import com.example.walletledger.adapters.system.SystemClockAdapter;
import com.example.walletledger.adapters.system.UuidAccountIdGenerator;
import com.example.walletledger.adapters.system.UuidTransferIdGenerator;
import com.example.walletledger.usecases.spi.AccountIdGenerator;
import com.example.walletledger.usecases.spi.AccountSpiPort;
import com.example.walletledger.usecases.spi.ClockSpiPort;
import com.example.walletledger.usecases.spi.IdempotencyRecordSpiPort;
import com.example.walletledger.usecases.spi.LedgerJournalSpiPort;
import com.example.walletledger.usecases.spi.LedgerTransactionSpiPort;
import com.example.walletledger.usecases.spi.TransferIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PersistenceConfiguration {

    @Bean
    public InMemoryLedgerStore inMemoryLedgerStore() {
        return new InMemoryLedgerStore();
    }

    @Bean
    public AccountLockRegistry accountLockRegistry() {
        return new AccountLockRegistry();
    }

    @Bean
    public LedgerTransactionSpiPort ledgerTransactionAdapter(InMemoryLedgerStore inMemoryLedgerStore,
                                                             AccountLockRegistry accountLockRegistry) {
        return new InMemoryLedgerTransactionAdapter(inMemoryLedgerStore, accountLockRegistry);
    }

    @Bean
    public AccountSpiPort accountAdapter(InMemoryLedgerStore inMemoryLedgerStore) {
        return new InMemoryAccountAdapter(inMemoryLedgerStore);
    }

    @Bean
    public LedgerJournalSpiPort ledgerJournalAdapter(InMemoryLedgerStore inMemoryLedgerStore) {
        return new InMemoryLedgerJournalAdapter(inMemoryLedgerStore);
    }

    @Bean
    public IdempotencyRecordSpiPort idempotencyRecordAdapter(LedgerProperties ledgerProperties) {
        return new CaffeineIdempotencyRecordAdapter(ledgerProperties.idempotencyMaximumRecords(),
                ledgerProperties.idempotencyRetention());
    }

    @Bean
    public ClockSpiPort systemClockAdapter() {
        return new SystemClockAdapter();
    }

    @Bean
    public AccountIdGenerator accountIdGenerator() {
        return new UuidAccountIdGenerator();
    }

    @Bean
    public TransferIdGenerator transferIdGenerator() {
        return new UuidTransferIdGenerator();
    }
}
