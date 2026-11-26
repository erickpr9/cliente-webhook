package com.estudos.cliente.domain.model;

/**
 * Situação do pedido na loja, do ponto de vista da expedição.
 *
 * <pre>
 * AWAITING_PAYMENT ──confirmado──▶ READY_TO_SHIP ──estornado──▶ REFUNDED
 *        │
 *        └─────recusado──────▶ PAYMENT_FAILED
 * </pre>
 */
public enum FulfillmentStatus {
    /** Pedido feito, pagamento ainda não confirmado: não pode ser enviado. */
    AWAITING_PAYMENT("aguardando pagamento"),
    /** Pagamento confirmado: liberado para separação e envio. */
    READY_TO_SHIP("liberado para envio"),
    /** Pagamento recusado: o pedido não segue. */
    PAYMENT_FAILED("com pagamento recusado"),
    /** Pagamento estornado: o envio deve ser cancelado. */
    REFUNDED("estornado");

    private final String description;

    FulfillmentStatus(String description) {
        this.description = description;
    }

    /** Descrição em português, para mensagens de log. */
    public String description() {
        return description;
    }

    public boolean canTransitionTo(FulfillmentStatus target) {
        return switch (this) {
            case AWAITING_PAYMENT -> target == READY_TO_SHIP || target == PAYMENT_FAILED;
            case READY_TO_SHIP -> target == REFUNDED;
            case PAYMENT_FAILED, REFUNDED -> false;
        };
    }
}
