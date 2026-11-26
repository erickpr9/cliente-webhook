package com.estudos.cliente.api.simulation;

/**
 * Como a loja responde aos avisos, para exercitar a garantia de entrega da plataforma.
 */
public enum FailureMode {
    /** Funcionamento normal. */
    NONE,
    /** Loja fora do ar: responde {@code 503}. A plataforma tenta de novo com backoff. */
    UNAVAILABLE,
    /**
     * Loja lenta: demora mais que o timeout da plataforma, mas processa o aviso. A plataforma não
     * recebe a resposta e reenvia; a loja reconhece a reentrega como duplicada.
     */
    SLOW,
    /** Loja recusa o aviso com {@code 410}. A plataforma desiste na hora (DEAD). */
    REJECT
}
