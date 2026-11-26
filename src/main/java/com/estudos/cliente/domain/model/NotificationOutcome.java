package com.estudos.cliente.domain.model;

/**
 * Como o aviso foi tratado pela loja.
 */
public enum NotificationOutcome {
    /** O pedido mudou de status. */
    APPLIED,
    /** Registrado, mas sem efeito (tipo desconhecido, fora de ordem, divergente ou já aplicado). */
    IGNORED
}
