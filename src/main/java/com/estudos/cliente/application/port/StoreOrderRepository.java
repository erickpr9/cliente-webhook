package com.estudos.cliente.application.port;

import com.estudos.cliente.application.exception.ConcurrentUpdateException;
import com.estudos.cliente.domain.model.StoreOrder;

import java.time.Instant;
import java.util.Optional;

/**
 * Porta de persistência dos pedidos da loja.
 */
public interface StoreOrderRepository {

    Optional<StoreOrder> findById(String id);

    void insert(StoreOrder order, Instant now);

    /**
     * @throws ConcurrentUpdateException se outro processo alterou o pedido nesse meio-tempo
     */
    void update(StoreOrder order, Instant now);
}
