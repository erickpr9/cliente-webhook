package com.estudos.cliente.support;

import com.estudos.cliente.application.dto.ReceivePaymentNotificationCommand;

import java.time.Instant;

/**
 * Dados de teste reutilizáveis.
 */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-11-26T14:00:00Z");
    public static final String ORDER_ID = "ord_1042";
    public static final String PAYMENT_ID = "pay_001";
    public static final long AMOUNT = 15990;
    public static final String SECRET = "test-client-secret-with-at-least-32-characters";

    private TestData() {
    }

    public static String payload(String notificationId, String type, Instant occurredAt) {
        return payload(notificationId, type, occurredAt, ORDER_ID, PAYMENT_ID, AMOUNT);
    }

    public static String payload(String notificationId, String type, Instant occurredAt, String orderId,
                                 String paymentId, long amount) {
        return """
                {"id":"%s","type":"%s","occurred_at":"%s","data":{"order_id":"%s","payment_id":"%s","amount":%d,"currency":"BRL","payment_status":"CONFIRMED"}}"""
                .formatted(notificationId, type, occurredAt, orderId, paymentId, amount);
    }

    public static ReceivePaymentNotificationCommand command(String notificationId, String type, Instant occurredAt) {
        return new ReceivePaymentNotificationCommand(notificationId, type, ORDER_ID, PAYMENT_ID, AMOUNT, "BRL",
                occurredAt, payload(notificationId, type, occurredAt));
    }
}
