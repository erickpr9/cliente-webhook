package com.estudos.cliente.application.exception;

/**
 * Outro processo alterou o pedido entre a leitura e a escrita. Resulta em erro {@code 5xx}
 * para a plataforma, que reenvia o aviso: a nova tentativa relê o estado atualizado.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String orderId) {
        super("Order '" + orderId + "' was modified concurrently");
    }
}
