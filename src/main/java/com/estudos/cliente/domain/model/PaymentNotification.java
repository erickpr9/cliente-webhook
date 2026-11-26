package com.estudos.cliente.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Aviso de pagamento recebido da plataforma, já autenticado.
 *
 * @param notificationId identificador do aviso na plataforma (igual em todas as tentativas)
 * @param type           tipo informado pela plataforma, mesmo que desconhecido
 * @param orderId        pedido afetado
 * @param paymentId      pagamento afetado
 * @param amountInCents  valor em centavos
 * @param currency       moeda ISO 4217
 * @param occurredAt     instante do evento na plataforma
 * @param payload        corpo JSON original, para auditoria
 * @param receivedAt     instante do recebimento
 */
public record PaymentNotification(
        String notificationId,
        String type,
        String orderId,
        String paymentId,
        long amountInCents,
        String currency,
        Instant occurredAt,
        String payload,
        Instant receivedAt) {

    public PaymentNotification {
        Objects.requireNonNull(notificationId, "notificationId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
    }
}
