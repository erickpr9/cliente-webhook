package com.estudos.cliente.domain.model;

import com.estudos.cliente.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class StoreOrderTest {

    private static final Instant T1 = Instant.parse("2026-11-26T10:00:00Z");
    private static final Instant T2 = T1.plusSeconds(60);

    private static StoreOrder awaiting() {
        return StoreOrder.awaitingPayment(TestData.ORDER_ID, TestData.PAYMENT_ID, TestData.AMOUNT, "BRL");
    }

    private static StoreOrder withStatus(FulfillmentStatus status, Instant lastNotificationAt) {
        return StoreOrder.restore(TestData.ORDER_ID, TestData.PAYMENT_ID, TestData.AMOUNT, "BRL", status,
                lastNotificationAt, 3);
    }

    @Test
    void confirmationReleasesOrderForShipping() {
        StoreOrder order = awaiting();

        assertThat(order.apply(PaymentNotificationType.PAYMENT_CONFIRMED, T1)).isEqualTo(ApplyResult.APPLIED);
        assertThat(order.status()).isEqualTo(FulfillmentStatus.READY_TO_SHIP);
        assertThat(order.lastNotificationAt()).contains(T1);
    }

    @Test
    void refusalFailsTheOrder() {
        StoreOrder order = awaiting();

        assertThat(order.apply(PaymentNotificationType.PAYMENT_REFUSED, T1).applied()).isTrue();
        assertThat(order.status()).isEqualTo(FulfillmentStatus.PAYMENT_FAILED);
    }

    @Test
    void refundAfterConfirmationCancelsShipping() {
        StoreOrder order = withStatus(FulfillmentStatus.READY_TO_SHIP, T1);

        assertThat(order.apply(PaymentNotificationType.PAYMENT_REFUNDED, T2)).isEqualTo(ApplyResult.APPLIED);
        assertThat(order.status()).isEqualTo(FulfillmentStatus.REFUNDED);
    }

    @Test
    void olderNotificationIsStale() {
        StoreOrder order = withStatus(FulfillmentStatus.READY_TO_SHIP, T2);

        assertThat(order.apply(PaymentNotificationType.PAYMENT_REFUSED, T1)).isEqualTo(ApplyResult.STALE);
        assertThat(order.status()).isEqualTo(FulfillmentStatus.READY_TO_SHIP);
    }

    @Test
    void sameStatusIsAlreadyApplied() {
        StoreOrder order = withStatus(FulfillmentStatus.READY_TO_SHIP, T1);

        assertThat(order.apply(PaymentNotificationType.PAYMENT_CONFIRMED, T2)).isEqualTo(ApplyResult.ALREADY_APPLIED);
        assertThat(order.lastNotificationAt()).contains(T1);
    }

    @Test
    void forbiddenTransitionIsNotAllowed() {
        StoreOrder order = awaiting();

        assertThat(order.apply(PaymentNotificationType.PAYMENT_REFUNDED, T1)).isEqualTo(ApplyResult.NOT_ALLOWED);
        assertThat(order.status()).isEqualTo(FulfillmentStatus.AWAITING_PAYMENT);
    }

    @Test
    void matchesPaymentAndAmount() {
        StoreOrder order = awaiting();

        assertThat(order.matches(TestData.PAYMENT_ID, TestData.AMOUNT, "BRL")).isTrue();
        assertThat(order.matches("pay_other", TestData.AMOUNT, "BRL")).isFalse();
        assertThat(order.matches(TestData.PAYMENT_ID, 1, "BRL")).isFalse();
        assertThat(order.matches(TestData.PAYMENT_ID, TestData.AMOUNT, "USD")).isFalse();
    }

    @Test
    void restoreKeepsState() {
        StoreOrder order = withStatus(FulfillmentStatus.PAYMENT_FAILED, T1);

        assertThat(order.id()).isEqualTo(TestData.ORDER_ID);
        assertThat(order.paymentId()).isEqualTo(TestData.PAYMENT_ID);
        assertThat(order.amountInCents()).isEqualTo(TestData.AMOUNT);
        assertThat(order.currency()).isEqualTo("BRL");
        assertThat(order.version()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveAmount(long amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StoreOrder.awaitingPayment("ord", "pay", amount, "BRL"));
    }

    @Test
    void rejectsBlankFieldsAndNullArguments() {
        assertThatIllegalArgumentException().isThrownBy(() -> StoreOrder.awaitingPayment(" ", "pay", 1, "BRL"));
        assertThatNullPointerException().isThrownBy(() -> awaiting().apply(null, T1));
        assertThatNullPointerException()
                .isThrownBy(() -> awaiting().apply(PaymentNotificationType.PAYMENT_CONFIRMED, null));
    }

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "AWAITING_PAYMENT, READY_TO_SHIP,    true",
            "AWAITING_PAYMENT, PAYMENT_FAILED,   true",
            "AWAITING_PAYMENT, REFUNDED,         false",
            "READY_TO_SHIP,    REFUNDED,         true",
            "READY_TO_SHIP,    PAYMENT_FAILED,   false",
            "READY_TO_SHIP,    AWAITING_PAYMENT, false",
            "PAYMENT_FAILED,   READY_TO_SHIP,    false",
            "REFUNDED,         READY_TO_SHIP,    false"
    })
    void statusTransitions(FulfillmentStatus from, FulfillmentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({
            "payment.confirmed, PAYMENT_CONFIRMED, READY_TO_SHIP",
            "payment.refused,   PAYMENT_REFUSED,   PAYMENT_FAILED",
            "payment.refunded,  PAYMENT_REFUNDED,  REFUNDED"
    })
    void notificationTypes(String code, PaymentNotificationType type, FulfillmentStatus target) {
        assertThat(PaymentNotificationType.fromCode(code)).contains(type);
        assertThat(type.code()).isEqualTo(code);
        assertThat(type.targetStatus()).isEqualTo(target);
        assertThat(PaymentNotificationType.fromCode("customer.created")).isEmpty();
    }
}
