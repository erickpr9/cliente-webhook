package com.estudos.cliente.application.usecase;

import com.estudos.cliente.application.dto.StoreOrderView;
import com.estudos.cliente.application.port.StoreOrderRepository;
import com.estudos.cliente.domain.exception.StoreOrderNotFoundException;

import java.util.Objects;

/**
 * Consulta um pedido da loja.
 */
public final class GetStoreOrderUseCase {

    private final StoreOrderRepository orders;

    public GetStoreOrderUseCase(StoreOrderRepository orders) {
        this.orders = Objects.requireNonNull(orders);
    }

    public StoreOrderView execute(String orderId) {
        return orders.findById(orderId).map(StoreOrderView::from)
                .orElseThrow(() -> new StoreOrderNotFoundException(orderId));
    }
}
