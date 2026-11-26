package com.estudos.cliente.application.dto;

import java.time.Instant;

/**
 * Visão de leitura de um aviso recebido.
 */
public record ReceivedNotificationView(
        String notificationId,
        String type,
        String orderId,
        String paymentId,
        long amountInCents,
        Instant occurredAt,
        Instant receivedAt,
        String outcome,
        String outcomeNote) {
}
