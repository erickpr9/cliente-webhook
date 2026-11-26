package com.estudos.cliente.api.simulation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liga e desliga a falha simulada. Só existe com {@code cliente.simulation.enabled=true}.
 */
@RestController
@RequestMapping("/simulation/failure-mode")
@ConditionalOnProperty(prefix = "cliente.simulation", name = "enabled", havingValue = "true")
@Tag(name = "Simulation", description = "Simula a loja fora do ar (somente local)")
public class FailureSimulationController {

    private static final Logger log = LoggerFactory.getLogger(FailureSimulationController.class);

    private final FailureSimulation simulation;

    public FailureSimulationController(FailureSimulation simulation) {
        this.simulation = simulation;
    }

    @GetMapping
    @Operation(summary = "Modo de falha corrente")
    public FailureModeBody current() {
        return new FailureModeBody(simulation.mode());
    }

    @PutMapping
    @Operation(summary = "Troca o modo de falha: NONE, UNAVAILABLE, SLOW ou REJECT")
    public FailureModeBody change(@Valid @RequestBody FailureModeBody body) {
        simulation.change(body.mode());
        log.warn("Modo de falha simulada alterado para {}", body.mode());
        return new FailureModeBody(simulation.mode());
    }

    /**
     * @param mode modo de falha
     */
    public record FailureModeBody(@NotNull FailureMode mode) {
    }
}
