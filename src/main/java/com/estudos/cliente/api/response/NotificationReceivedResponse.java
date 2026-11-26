package com.estudos.cliente.api.response;

import com.estudos.cliente.application.dto.ReceiveResult;

/**
 * Resposta à plataforma. Qualquer {@code 2xx} confirma a entrega; o corpo é informativo.
 *
 * @param status         {@code processed}, {@code ignored} ou {@code duplicate}
 * @param notificationId id do aviso
 * @param note           explicação do resultado
 */
public record NotificationReceivedResponse(String status, String notificationId, String note) {

    public static NotificationReceivedResponse from(ReceiveResult result) {
        String status = result.duplicate() ? "duplicate"
                : "APPLIED".equals(result.outcome()) ? "processed" : "ignored";
        return new NotificationReceivedResponse(status, result.notificationId(), result.note());
    }
}
