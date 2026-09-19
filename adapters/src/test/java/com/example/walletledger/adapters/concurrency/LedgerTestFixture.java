package com.example.walletledger.adapters.concurrency;

import com.example.walletledger.adapters.persistence.memory.AccountLockRegistry;
import com.example.walletledger.adapters.persistence.memory.CaffeineIdempotencyRecordAdapter;
import com.example.walletledger.adapters.persistence.memory.InMemoryAccountAdapter;
import com.example.walletledger.adapters.persistence.memory.InMemoryLedgerJournalAdapter;
import com.example.walletledger.adapters.persistence.memory.InMemoryLedgerStore;
import com.example.walletledger.adapters.persistence.memory.InMemoryLedgerTransactionAdapter;
import com.example.walletledger.adapters.system.SystemClockAdapter;
import com.example.walletledger.adapters.system.UuidAccountIdGenerator;
import com.example.walletledger.adapters.system.UuidTransferIdGenerator;
import com.example.walletledger.core.model.Account;
import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.core.model.IdempotencyKey;
import com.example.walletledger.core.model.LedgerEntry;
import com.example.walletledger.core.model.Money;
import com.example.walletledger.usecases.api.CreateAccountCommand;
import com.example.walletledger.usecases.api.TransferCommand;
import com.example.walletledger.usecases.api.TransferReceipt;
import com.example.walletledger.usecases.service.CreateAccountService;
import com.example.walletledger.usecases.service.GetAccountBalanceService;
import com.example.walletledger.usecases.service.GetAccountEntriesService;
import com.example.walletledger.usecases.service.TransferMoneyService;
import com.example.walletledger.usecases.spi.ClockSpiPort;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class LedgerTestFixture {

    static final Currency EURO = Currency.getInstance("EUR");
    static final Currency DOLLAR = Currency.getInstance("USD");

    private static final Duration IN_FLIGHT_WAIT_BUDGET = Duration.ofSeconds(20);
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofMinutes(30);
    private static final long MAXIMUM_IDEMPOTENCY_RECORDS = 1_000_000L;

    private final AtomicLong openedAccountCounter = new AtomicLong();
    private final InMemoryLedgerStore store = new InMemoryLedgerStore();
    private final AccountLockRegistry accountLockRegistry = new AccountLockRegistry();
    private final LatchingLedgerTransactionAdapter ledgerTransactions =
            new LatchingLedgerTransactionAdapter(new InMemoryLedgerTransactionAdapter(store, accountLockRegistry));
    private final CaffeineIdempotencyRecordAdapter idempotencyRecords =
            new CaffeineIdempotencyRecordAdapter(MAXIMUM_IDEMPOTENCY_RECORDS, IDEMPOTENCY_RETENTION);
    private final InMemoryAccountAdapter accounts = new InMemoryAccountAdapter(store);
    private final InMemoryLedgerJournalAdapter ledgerJournal = new InMemoryLedgerJournalAdapter(store);
    private final ClockSpiPort clock = new SystemClockAdapter();
    private final CreateAccountService createAccount = new CreateAccountService(ledgerTransactions,
            new UuidAccountIdGenerator(), new UuidTransferIdGenerator(), clock);
    private final TransferMoneyService transferMoney = new TransferMoneyService(ledgerTransactions,
            idempotencyRecords, new UuidTransferIdGenerator(), clock, IN_FLIGHT_WAIT_BUDGET);
    private final GetAccountBalanceService accountBalance = new GetAccountBalanceService(ledgerTransactions, clock);
    private final GetAccountEntriesService accountEntries = new GetAccountEntriesService(accounts, ledgerJournal);

    LatchingLedgerTransactionAdapter latchingLedger() {
        return ledgerTransactions;
    }

    AccountId openAccount(String openingBalance) {
        return openAccount(openingBalance, EURO);
    }

    AccountId openAccount(String openingBalance, Currency currency) {
        var ownerReference = "owner-" + openedAccountCounter.incrementAndGet();
        return createAccount.createAccount(
                new CreateAccountCommand(ownerReference, currency, new BigDecimal(openingBalance))).accountId();
    }

    TransferReceipt transfer(AccountId sourceAccountId, AccountId targetAccountId, String amount,
                             String idempotencyKey) {
        return transfer(sourceAccountId, targetAccountId, euros(amount), idempotencyKey);
    }

    TransferReceipt transfer(AccountId sourceAccountId, AccountId targetAccountId, Money amount,
                             String idempotencyKey) {
        return transferMoney.transfer(new TransferCommand(IdempotencyKey.of(idempotencyKey), sourceAccountId,
                targetAccountId, amount));
    }

    Money balanceOf(AccountId accountId) {
        return accountBalance.getBalance(accountId).balance();
    }

    List<LedgerEntry> entriesOf(AccountId accountId) {
        return accountEntries.findEntries(accountId);
    }

    List<LedgerEntry> allEntries() {
        return store.allEntries();
    }

    List<Account> allAccounts() {
        return store.allAccounts();
    }

    Money totalBalance() {
        return store.allAccounts().stream()
                .map(Account::balance)
                .reduce(Money.zero(EURO), Money::add);
    }

    static Money euros(String amount) {
        return Money.of(new BigDecimal(amount), EURO);
    }

    static Money dollars(String amount) {
        return Money.of(new BigDecimal(amount), DOLLAR);
    }
}
