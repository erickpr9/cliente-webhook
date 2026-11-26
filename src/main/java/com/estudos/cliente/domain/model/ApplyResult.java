package com.estudos.cliente.domain.model;

/**
 * Resultado da tentativa de aplicar um aviso ao pedido.
 */
public enum ApplyResult {
    /** O status do pedido mudou. */
    APPLIED,
    /** O pedido já estava no status de destino. */
    ALREADY_APPLIED,
    /** O aviso é mais antigo que o último aplicado (chegou fora de ordem). */
    STALE,
    /** A mudança não é permitida a partir do status atual. */
    NOT_ALLOWED;

    public boolean applied() {
        return this == APPLIED;
    }
}
