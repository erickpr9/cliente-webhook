package com.estudos.cliente.application.dto;

import java.time.Instant;

/**
 * Aviso autenticado e validado pela API, pronto para ser tratado.
 */
public record ReceivePaymentNotificationCommand(
        String notificationId,
        String type,
        String orderId,
        String paymentId,
        long amountInCents,
        String currency,
        Instant occurredAt,
        String payload) {
}
