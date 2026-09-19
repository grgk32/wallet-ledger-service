package com.example.walletledger.usecases.spi;

@FunctionalInterface
public interface LockContentionSpiPort {

    long countTrackedAccounts();
}
