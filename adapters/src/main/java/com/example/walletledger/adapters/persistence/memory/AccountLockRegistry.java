package com.example.walletledger.adapters.persistence.memory;

import com.example.walletledger.core.model.AccountId;
import com.example.walletledger.usecases.spi.LockContentionSpiPort;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public final class AccountLockRegistry implements LockContentionSpiPort {

    private final ConcurrentMap<AccountId, ReentrantLock> locksByAccount = new ConcurrentHashMap<>();

    public List<ReentrantLock> acquireAll(Collection<AccountId> participants) {
        var orderedParticipants = participants.stream().sorted().toList();
        var acquiredLocks = new ArrayList<ReentrantLock>(orderedParticipants.size());
        for (var accountId : orderedParticipants) {
            var accountLock = locksByAccount.computeIfAbsent(accountId, id -> new ReentrantLock());
            accountLock.lock();
            acquiredLocks.add(accountLock);
        }
        return acquiredLocks;
    }

    public void releaseAll(List<ReentrantLock> acquiredLocks) {
        for (var index = acquiredLocks.size() - 1; index >= 0; index--) {
            acquiredLocks.get(index).unlock();
        }
    }

    @Override
    public long countTrackedAccounts() {
        return locksByAccount.size();
    }
}
