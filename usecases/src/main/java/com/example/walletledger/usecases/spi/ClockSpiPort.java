package com.example.walletledger.usecases.spi;

import java.time.Instant;

@FunctionalInterface
public interface ClockSpiPort {

    Instant now();
}
