package com.estudos.cliente.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Pedido da loja. O status muda conforme os avisos de pagamento da plataforma.
 */
public final class StoreOrder {

    private final String id;
    private final String paymentId;
    private final long amountInCents;
    private final String currency;
    private final long version;
    private FulfillmentStatus status;
    private Instant lastNotificationAt;

    private StoreOrder(String id, String paymentId, long amountInCents, String currency, FulfillmentStatus status,
                       Instant lastNotificationAt, long version) {
        this.id = requireText(id, "id");
        this.paymentId = requireText(paymentId, "paymentId");
        if (amountInCents <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        this.amountInCents = amountInCents;
        this.currency = requireText(currency, "currency");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.lastNotificationAt = lastNotificationAt;
        this.version = version;
    }

    /**
     * Pedido aguardando pagamento.
     *
     * <p>Numa loja real o pedido nasce no checkout. Aqui ele também pode nascer do primeiro aviso,
     * porque os pedidos de exemplo são criados na plataforma de pagamento.
     */
    public static StoreOrder awaitingPayment(String id, String paymentId, long amountInCents, String currency) {
        return new StoreOrder(id, paymentId, amountInCents, currency, FulfillmentStatus.AWAITING_PAYMENT, null, 0);
    }

    public static StoreOrder restore(String id, String paymentId, long amountInCents, String currency,
                                     FulfillmentStatus status, Instant lastNotificationAt, long version) {
        return new StoreOrder(id, paymentId, amountInCents, currency, status, lastNotificationAt, version);
    }

    /**
     * Aplica um aviso de pagamento. Regras, nesta ordem: aviso mais antigo que o último aplicado é
     * descartado; aviso que não muda nada é ignorado; mudança proibida não altera o pedido.
     */
    public ApplyResult apply(PaymentNotificationType type, Instant occurredAt) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");

        if (lastNotificationAt != null && occurredAt.isBefore(lastNotificationAt)) {
            return ApplyResult.STALE;
        }
        FulfillmentStatus target = type.targetStatus();
        if (status == target) {
            return ApplyResult.ALREADY_APPLIED;
        }
        if (!status.canTransitionTo(target)) {
            return ApplyResult.NOT_ALLOWED;
        }
        status = target;
        lastNotificationAt = occurredAt;
        return ApplyResult.APPLIED;
    }

    /** Indica se pagamento e valor do aviso batem com o pedido. */
    public boolean matches(String otherPaymentId, long otherAmountInCents, String otherCurrency) {
        return paymentId.equals(otherPaymentId) && amountInCents == otherAmountInCents
                && currency.equals(otherCurrency);
    }

    public String id() {
        return id;
    }

    public String paymentId() {
        return paymentId;
    }

    public long amountInCents() {
        return amountInCents;
    }

    public String currency() {
        return currency;
    }

    public FulfillmentStatus status() {
        return status;
    }

    public Optional<Instant> lastNotificationAt() {
        return Optional.ofNullable(lastNotificationAt);
    }

    public long version() {
        return version;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
