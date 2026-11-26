package com.estudos.cliente.application.usecase;

import com.estudos.cliente.application.dto.ReceiveResult;
import com.estudos.cliente.application.dto.StoreOrderView;
import com.estudos.cliente.application.port.PaymentNotificationRepository;
import com.estudos.cliente.application.port.StoreOrderRepository;
import com.estudos.cliente.application.port.TransactionRunner;
import com.estudos.cliente.domain.exception.StoreOrderNotFoundException;
import com.estudos.cliente.domain.model.FulfillmentStatus;
import com.estudos.cliente.domain.model.NotificationOutcome;
import com.estudos.cliente.domain.model.PaymentNotification;
import com.estudos.cliente.domain.model.StoreOrder;
import com.estudos.cliente.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivePaymentNotificationUseCaseTest {

    private static final Instant OCCURRED_AT = TestData.NOW.minusSeconds(5);

    @Mock
    private PaymentNotificationRepository notifications;
    @Mock
    private StoreOrderRepository orders;

    private final TransactionRunner directTransactions = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private ReceivePaymentNotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReceivePaymentNotificationUseCase(notifications, orders, directTransactions,
                Clock.fixed(TestData.NOW, ZoneOffset.UTC));
    }

    private static StoreOrder order(FulfillmentStatus status) {
        return StoreOrder.restore(TestData.ORDER_ID, TestData.PAYMENT_ID, TestData.AMOUNT, "BRL", status,
                OCCURRED_AT.minusSeconds(60), 0);
    }

    @Test
    void firstConfirmationCreatesOrderReadyToShip() {
        when(notifications.insertIfAbsent(any())).thenReturn(true);
        when(orders.findById(TestData.ORDER_ID)).thenReturn(Optional.empty());

        ReceiveResult result = useCase.execute(TestData.command("ntf_1", "payment.confirmed", OCCURRED_AT));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.outcome()).isEqualTo("APPLIED");
        ArgumentCaptor<StoreOrder> saved = ArgumentCaptor.forClass(StoreOrder.class);
        verify(orders).insert(saved.capture(), eq(TestData.NOW));
        assertThat(saved.getValue().status()).isEqualTo(FulfillmentStatus.READY_TO_SHIP);
        verify(notifications).recordOutcome("ntf_1", NotificationOutcome.APPLIED, "Order READY_TO_SHIP");

        ArgumentCaptor<PaymentNotification> stored = ArgumentCaptor.forClass(PaymentNotification.class);
        verify(notifications).insertIfAbsent(stored.capture());
        assertThat(stored.getValue().receivedAt()).isEqualTo(TestData.NOW);
        assertThat(stored.getValue().amountInCents()).isEqualTo(TestData.AMOUNT);
    }

    @Test
    void knownOrderIsUpdated() {
        StoreOrder existing = order(FulfillmentStatus.READY_TO_SHIP);
        when(notifications.insertIfAbsent(any())).thenReturn(true);
        when(orders.findById(TestData.ORDER_ID)).thenReturn(Optional.of(existing));

        ReceiveResult result = useCase.execute(TestData.command("ntf_2", "payment.refunded", OCCURRED_AT));

        assertThat(result.outcome()).isEqualTo("APPLIED");
        assertThat(existing.status()).isEqualTo(FulfillmentStatus.REFUNDED);
        verify(orders).update(existing, TestData.NOW);
        verify(orders, never()).insert(any(), any());
    }

    @Test
    void redeliveryIsRecognizedWithoutSideEffects() {
        when(notifications.insertIfAbsent(any())).thenReturn(false);

        ReceiveResult result = useCase.execute(TestData.command("ntf_1", "payment.confirmed", OCCURRED_AT));

        assertThat(result.duplicate()).isTrue();
        verifyNoInteractions(orders);
        verify(notifications, never()).recordOutcome(any(), any(), any());
    }

    @Test
    void unknownTypeIsIgnored() {
        when(notifications.insertIfAbsent(any())).thenReturn(true);

        ReceiveResult result = useCase.execute(TestData.command("ntf_3", "customer.created", OCCURRED_AT));

        assertThat(result.outcome()).isEqualTo("IGNORED");
        assertThat(result.note()).contains("customer.created");
        verifyNoInteractions(orders);
    }

    @Test
    void mismatchedAmountIsIgnoredForManualReview() {
        when(notifications.insertIfAbsent(any())).thenReturn(true);
        when(orders.findById(TestData.ORDER_ID)).thenReturn(Optional.of(StoreOrder.restore(TestData.ORDER_ID,
                TestData.PAYMENT_ID, 1, "BRL", FulfillmentStatus.AWAITING_PAYMENT, null, 0)));

        ReceiveResult result = useCase.execute(TestData.command("ntf_4", "payment.confirmed", OCCURRED_AT));

        assertThat(result.outcome()).isEqualTo("IGNORED");
        assertThat(result.note()).contains("does not match");
        verify(orders, never()).update(any(), any());
    }

    @Test
    void notApplicableTransitionIsIgnored() {
        when(notifications.insertIfAbsent(any())).thenReturn(true);
        when(orders.findById(TestData.ORDER_ID)).thenReturn(Optional.of(order(FulfillmentStatus.READY_TO_SHIP)));

        ReceiveResult result = useCase.execute(TestData.command("ntf_5", "payment.refused", OCCURRED_AT));

        assertThat(result.outcome()).isEqualTo("IGNORED");
        assertThat(result.note()).isEqualTo("Not applicable: NOT_ALLOWED");
        verify(notifications).recordOutcome("ntf_5", NotificationOutcome.IGNORED, "Not applicable: NOT_ALLOWED");
    }

    @Test
    void queriesReturnViewsOrNotFound() {
        when(orders.findById(TestData.ORDER_ID)).thenReturn(Optional.of(order(FulfillmentStatus.READY_TO_SHIP)));
        when(orders.findById("ord_x")).thenReturn(Optional.empty());
        when(notifications.findRecent(ListReceivedNotificationsUseCase.MAX_LIMIT)).thenReturn(List.of());
        var getOrder = new GetStoreOrderUseCase(orders);

        StoreOrderView view = getOrder.execute(TestData.ORDER_ID);

        assertThat(view.readyToShip()).isTrue();
        assertThat(view.status()).isEqualTo("READY_TO_SHIP");
        assertThatThrownBy(() -> getOrder.execute("ord_x")).isInstanceOf(StoreOrderNotFoundException.class);
        assertThat(new ListReceivedNotificationsUseCase(notifications).execute(9999)).isEmpty();
    }
}
