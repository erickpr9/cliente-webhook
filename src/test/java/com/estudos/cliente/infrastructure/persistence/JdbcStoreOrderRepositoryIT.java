package com.estudos.cliente.infrastructure.persistence;

import com.estudos.cliente.application.exception.ConcurrentUpdateException;
import com.estudos.cliente.domain.model.FulfillmentStatus;
import com.estudos.cliente.domain.model.PaymentNotificationType;
import com.estudos.cliente.domain.model.StoreOrder;
import com.estudos.cliente.support.PostgresIntegrationTest;
import com.estudos.cliente.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcStoreOrderRepositoryIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcStoreOrderRepository orders;

    @Test
    void roundTripsAndUpdatesWithVersion() {
        orders.insert(StoreOrder.awaitingPayment(TestData.ORDER_ID, TestData.PAYMENT_ID, TestData.AMOUNT, "BRL"),
                TestData.NOW);

        StoreOrder order = orders.findById(TestData.ORDER_ID).orElseThrow();
        order.apply(PaymentNotificationType.PAYMENT_CONFIRMED, TestData.NOW);
        orders.update(order, TestData.NOW);

        StoreOrder stored = orders.findById(TestData.ORDER_ID).orElseThrow();
        assertThat(stored.status()).isEqualTo(FulfillmentStatus.READY_TO_SHIP);
        assertThat(stored.lastNotificationAt()).contains(TestData.NOW);
        assertThat(stored.version()).isEqualTo(1);
        assertThat(orders.findById("ord_missing")).isEmpty();
    }

    @Test
    void staleVersionIsRejected() {
        orders.insert(StoreOrder.awaitingPayment(TestData.ORDER_ID, TestData.PAYMENT_ID, TestData.AMOUNT, "BRL"),
                TestData.NOW);
        StoreOrder first = orders.findById(TestData.ORDER_ID).orElseThrow();
        StoreOrder second = orders.findById(TestData.ORDER_ID).orElseThrow();
        first.apply(PaymentNotificationType.PAYMENT_CONFIRMED, TestData.NOW);
        second.apply(PaymentNotificationType.PAYMENT_REFUSED, TestData.NOW);

        orders.update(first, TestData.NOW);

        assertThatThrownBy(() -> orders.update(second, TestData.NOW)).isInstanceOf(ConcurrentUpdateException.class);
    }
}
