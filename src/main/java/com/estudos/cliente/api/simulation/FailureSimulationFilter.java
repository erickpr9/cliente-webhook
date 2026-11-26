package com.estudos.cliente.api.simulation;

import com.estudos.cliente.api.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * Aplica o modo de falha simulado às requisições de aviso, antes de chegarem ao controller.
 */
public final class FailureSimulationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FailureSimulationFilter.class);

    private final FailureSimulation simulation;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FailureSimulationFilter(FailureSimulation simulation, ObjectMapper objectMapper, Clock clock) {
        this.simulation = simulation;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        switch (simulation.mode()) {
            case UNAVAILABLE -> reject(request, response, HttpStatus.SERVICE_UNAVAILABLE);
            case REJECT -> reject(request, response, HttpStatus.GONE);
            case SLOW -> {
                log.warn("Falha simulada (loja lenta): segurando o aviso por {}s antes de processar. A plataforma deve "
                        + "desistir de esperar e reenviar.", simulation.slowDelay().toSeconds());
                pause();
                chain.doFilter(request, response);
            }
            case NONE -> chain.doFilter(request, response);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, HttpStatus status)
            throws IOException {
        String label = simulation.mode() == FailureMode.UNAVAILABLE ? "loja fora do ar" : "loja recusando";
        log.warn("Falha simulada ({}): respondendo {} sem processar o aviso", label, status.value());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(clock, status,
                "Simulated failure: " + simulation.mode(), request.getRequestURI(), List.of()));
    }

    private void pause() {
        try {
            Thread.sleep(simulation.slowDelay());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
