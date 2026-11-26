package com.estudos.cliente.application.dto;

import com.estudos.cliente.domain.model.FulfillmentStatus;
import com.estudos.cliente.domain.model.StoreOrder;

import java.time.Instant;

/**
 * Visão de leitura de um pedido da loja.
 *
 * @param readyToShip {@code true} quando a expedição pode separar e enviar o pedido
 */
public record StoreOrderView(String orderId, String paymentId, long amountInCents, String currency, String status,
                             boolean readyToShip, Instant lastNotificationAt) {

    public static StoreOrderView from(StoreOrder order) {
        return new StoreOrderView(order.id(), order.paymentId(), order.amountInCents(), order.currency(),
                order.status().name(), order.status() == FulfillmentStatus.READY_TO_SHIP,
                order.lastNotificationAt().orElse(null));
    }
}
