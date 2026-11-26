package com.estudos.cliente.api.controller;

import com.estudos.cliente.application.dto.ReceivedNotificationView;
import com.estudos.cliente.application.dto.StoreOrderView;
import com.estudos.cliente.application.usecase.GetStoreOrderUseCase;
import com.estudos.cliente.application.usecase.ListReceivedNotificationsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consultas da loja: pedidos e avisos recebidos.
 */
@RestController
@Tag(name = "Store", description = "Pedidos da loja e avisos recebidos")
public class StoreQueryController {

    private final GetStoreOrderUseCase getOrder;
    private final ListReceivedNotificationsUseCase listNotifications;

    public StoreQueryController(GetStoreOrderUseCase getOrder, ListReceivedNotificationsUseCase listNotifications) {
        this.getOrder = getOrder;
        this.listNotifications = listNotifications;
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Situação do pedido na loja (liberado para envio ou não)")
    public StoreOrderView order(@PathVariable String orderId) {
        return getOrder.execute(orderId);
    }

    @GetMapping("/notifications")
    @Operation(summary = "Avisos recebidos da plataforma, mais recentes primeiro")
    public List<ReceivedNotificationView> notifications(@RequestParam(defaultValue = "20") int limit) {
        return listNotifications.execute(limit);
    }
}
