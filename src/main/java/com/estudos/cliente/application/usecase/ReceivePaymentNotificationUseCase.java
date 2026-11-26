package com.estudos.cliente.application.usecase;

import com.estudos.cliente.application.dto.ReceivePaymentNotificationCommand;
import com.estudos.cliente.application.dto.ReceiveResult;
import com.estudos.cliente.application.port.PaymentNotificationRepository;
import com.estudos.cliente.application.port.StoreOrderRepository;
import com.estudos.cliente.application.port.TransactionRunner;
import com.estudos.cliente.domain.model.ApplyResult;
import com.estudos.cliente.domain.model.FulfillmentStatus;
import com.estudos.cliente.domain.model.NotificationOutcome;
import com.estudos.cliente.domain.model.PaymentNotification;
import com.estudos.cliente.domain.model.PaymentNotificationType;
import com.estudos.cliente.domain.model.StoreOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Trata um aviso de pagamento da plataforma.
 *
 * <p>Registro do aviso, mudança no pedido e resultado ficam na mesma transação. Se algo falhar,
 * nada é gravado e a API responde erro: a plataforma reenvia o mesmo aviso depois. Se o aviso já
 * foi tratado, a reentrega é reconhecida pelo id e não tem efeito.
 *
 * <p>Avisos que não se aplicam (fora de ordem, divergentes, tipo desconhecido) são registrados
 * como {@code IGNORED} e respondidos com sucesso. Responder erro faria a plataforma reenviar algo
 * que nunca vai dar certo.
 */
public final class ReceivePaymentNotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReceivePaymentNotificationUseCase.class);

    private final PaymentNotificationRepository notifications;
    private final StoreOrderRepository orders;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ReceivePaymentNotificationUseCase(PaymentNotificationRepository notifications, StoreOrderRepository orders,
                                             TransactionRunner transactions, Clock clock) {
        this.notifications = Objects.requireNonNull(notifications);
        this.orders = Objects.requireNonNull(orders);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    public ReceiveResult execute(ReceivePaymentNotificationCommand command) {
        return transactions.inTransaction(() -> handle(command));
    }

    private ReceiveResult handle(ReceivePaymentNotificationCommand command) {
        Instant now = clock.instant();
        boolean firstDelivery = notifications.insertIfAbsent(new PaymentNotification(command.notificationId(),
                command.type(), command.orderId(), command.paymentId(), command.amountInCents(), command.currency(),
                command.occurredAt(), command.payload(), now));
        if (!firstDelivery) {
            log.info("Aviso repetido: este aviso já tinha sido recebido antes. Reentrega ignorada, nada foi alterado "
                    + "no pedido {}", command.orderId());
            return ReceiveResult.duplicate(command.notificationId());
        }

        Optional<PaymentNotificationType> type = PaymentNotificationType.fromCode(command.type());
        log.info("Aviso recebido da plataforma: {} | pedido {} | pagamento {} | valor {}",
                type.map(PaymentNotificationType::description).orElse("tipo desconhecido (" + command.type() + ")"),
                command.orderId(), command.paymentId(), MoneyFormat.format(command.amountInCents(), command.currency()));

        if (type.isEmpty()) {
            return ignore(command, "Unsupported notification type " + command.type(),
                    "a loja não trata avisos do tipo '" + command.type() + "'");
        }

        Optional<StoreOrder> existing = orders.findById(command.orderId());
        StoreOrder order = existing.orElseGet(() -> StoreOrder.awaitingPayment(command.orderId(), command.paymentId(),
                command.amountInCents(), command.currency()));

        if (!order.matches(command.paymentId(), command.amountInCents(), command.currency())) {
            log.warn("ATENÇÃO: o aviso não confere com o pedido {}. A loja esperava o pagamento {} de {}, mas recebeu "
                            + "o pagamento {} de {}. Pedido NÃO liberado: revisar manualmente.",
                    order.id(), order.paymentId(), MoneyFormat.format(order.amountInCents(), order.currency()),
                    command.paymentId(), MoneyFormat.format(command.amountInCents(), command.currency()));
            return finish(command, NotificationOutcome.IGNORED, "Payment or amount does not match the order");
        }

        FulfillmentStatus previous = order.status();
        ApplyResult result = order.apply(type.get(), command.occurredAt());
        if (!result.applied()) {
            return ignore(command, "Not applicable: " + result, reason(result, previous, type.get()));
        }

        if (existing.isPresent()) {
            orders.update(order, now);
        } else {
            orders.insert(order, now);
        }
        log.info(appliedMessage(order));
        return finish(command, NotificationOutcome.APPLIED, "Order " + order.status());
    }

    private ReceiveResult ignore(ReceivePaymentNotificationCommand command, String note, String reason) {
        log.info("Aviso ignorado, o pedido {} não mudou: {}", command.orderId(), reason);
        return finish(command, NotificationOutcome.IGNORED, note);
    }

    private ReceiveResult finish(ReceivePaymentNotificationCommand command, NotificationOutcome outcome, String note) {
        notifications.recordOutcome(command.notificationId(), outcome, note);
        return new ReceiveResult(command.notificationId(), false, outcome.name(), note);
    }

    private static String appliedMessage(StoreOrder order) {
        return switch (order.status()) {
            case READY_TO_SHIP -> "Pedido " + order.id() + " LIBERADO PARA ENVIO: pagamento confirmado, "
                    + "a expedição já pode separar e despachar a mercadoria";
            case PAYMENT_FAILED -> "Pedido " + order.id() + " NÃO será enviado: o pagamento foi recusado";
            case REFUNDED -> "Pedido " + order.id() + " ESTORNADO: cancelar o envio, se a mercadoria ainda não saiu";
            case AWAITING_PAYMENT -> "Pedido " + order.id() + " aguardando pagamento";
        };
    }

    private static String reason(ApplyResult result, FulfillmentStatus current, PaymentNotificationType type) {
        return switch (result) {
            case STALE -> "o aviso é mais antigo que o último já aplicado (chegou fora de ordem)";
            case ALREADY_APPLIED -> "o pedido já estava " + current.description();
            case NOT_ALLOWED -> "um pedido " + current.description() + " não pode ficar "
                    + type.targetStatus().description();
            case APPLIED -> "";
        };
    }
}
