package com.example.walletledger.adapters.system;

import com.example.walletledger.usecases.spi.ClockSpiPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class SystemClockAdapter implements ClockSpiPort {

    private final Clock clock;

    public SystemClockAdapter() {
        this(Clock.systemUTC());
    }

    public SystemClockAdapter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
