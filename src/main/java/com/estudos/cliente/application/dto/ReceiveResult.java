package com.estudos.cliente.application.dto;

/**
 * Resultado do tratamento de um aviso.
 *
 * @param notificationId id do aviso
 * @param duplicate      {@code true} se era uma reentrega de aviso já tratado
 * @param outcome        {@code APPLIED} ou {@code IGNORED}; vazio quando duplicado
 * @param note           explicação do resultado
 */
public record ReceiveResult(String notificationId, boolean duplicate, String outcome, String note) {

    public static ReceiveResult duplicate(String notificationId) {
        return new ReceiveResult(notificationId, true, null, "Notification already received");
    }
}
