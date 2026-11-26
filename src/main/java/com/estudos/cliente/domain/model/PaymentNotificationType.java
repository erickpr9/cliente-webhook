package com.estudos.cliente.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de aviso enviados pela plataforma e o efeito de cada um no pedido da loja.
 */
public enum PaymentNotificationType {
    PAYMENT_CONFIRMED("payment.confirmed", FulfillmentStatus.READY_TO_SHIP, "pagamento confirmado"),
    PAYMENT_REFUSED("payment.refused", FulfillmentStatus.PAYMENT_FAILED, "pagamento recusado"),
    PAYMENT_REFUNDED("payment.refunded", FulfillmentStatus.REFUNDED, "pagamento estornado");

    private final String code;
    private final FulfillmentStatus targetStatus;
    private final String description;

    PaymentNotificationType(String code, FulfillmentStatus targetStatus, String description) {
        this.code = code;
        this.targetStatus = targetStatus;
        this.description = description;
    }

    /** Descrição em português, para mensagens de log. */
    public String description() {
        return description;
    }

    public String code() {
        return code;
    }

    public FulfillmentStatus targetStatus() {
        return targetStatus;
    }

    /** Tipos desconhecidos retornam vazio: são registrados e ignorados, sem erro. */
    public static Optional<PaymentNotificationType> fromCode(String code) {
        return Arrays.stream(values()).filter(type -> type.code.equals(code)).findFirst();
    }
}
