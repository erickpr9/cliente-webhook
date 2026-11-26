package com.estudos.cliente.api.simulation;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Modo de falha corrente, em memória (volta para {@link FailureMode#NONE} ao reiniciar).
 */
public final class FailureSimulation {

    private final AtomicReference<FailureMode> mode = new AtomicReference<>(FailureMode.NONE);
    private final Duration slowDelay;

    public FailureSimulation(Duration slowDelay) {
        this.slowDelay = Objects.requireNonNull(slowDelay);
    }

    public FailureMode mode() {
        return mode.get();
    }

    public void change(FailureMode newMode) {
        mode.set(Objects.requireNonNull(newMode));
    }

    public Duration slowDelay() {
        return slowDelay;
    }
}
