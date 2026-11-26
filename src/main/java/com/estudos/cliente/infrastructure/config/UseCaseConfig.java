package com.estudos.cliente.infrastructure.config;

import com.estudos.cliente.application.port.PaymentNotificationRepository;
import com.estudos.cliente.application.port.StoreOrderRepository;
import com.estudos.cliente.application.port.TransactionRunner;
import com.estudos.cliente.application.usecase.GetStoreOrderUseCase;
import com.estudos.cliente.application.usecase.ListReceivedNotificationsUseCase;
import com.estudos.cliente.application.usecase.ReceivePaymentNotificationUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Monta os casos de uso. A camada de aplicação não conhece o Spring.
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ReceivePaymentNotificationUseCase receivePaymentNotificationUseCase(PaymentNotificationRepository notifications,
                                                                        StoreOrderRepository orders,
                                                                        TransactionRunner transactions, Clock clock) {
        return new ReceivePaymentNotificationUseCase(notifications, orders, transactions, clock);
    }

    @Bean
    GetStoreOrderUseCase getStoreOrderUseCase(StoreOrderRepository orders) {
        return new GetStoreOrderUseCase(orders);
    }

    @Bean
    ListReceivedNotificationsUseCase listReceivedNotificationsUseCase(PaymentNotificationRepository notifications) {
        return new ListReceivedNotificationsUseCase(notifications);
    }
}
