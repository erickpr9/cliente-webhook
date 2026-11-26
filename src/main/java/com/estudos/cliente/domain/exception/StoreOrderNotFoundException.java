package com.estudos.cliente.domain.exception;

/**
 * Pedido inexistente na loja. Na API vira {@code 404}.
 */
public class StoreOrderNotFoundException extends RuntimeException {

    public StoreOrderNotFoundException(String orderId) {
        super("Order '" + orderId + "' not found");
    }
}
