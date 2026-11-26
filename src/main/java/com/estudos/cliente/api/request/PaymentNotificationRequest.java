package com.estudos.cliente.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Contrato do aviso enviado pela plataforma:
 *
 * <pre>{@code
 * {"id":"...","type":"payment.confirmed","occurred_at":"...",
 *  "data":{"order_id":"...","payment_id":"...","amount":15990,"currency":"BRL","payment_status":"CONFIRMED"}}
 * }</pre>
 */
public record PaymentNotificationRequest(
        @NotBlank @Size(max = 100) String id,
        @NotBlank @Size(max = 50) String type,
        @NotNull @JsonProperty("occurred_at") Instant occurredAt,
        @NotNull @Valid Data data) {

    /**
     * Dados do pagamento.
     */
    public record Data(
            @NotBlank @Size(max = 64) @JsonProperty("order_id") String orderId,
            @NotBlank @Size(max = 64) @JsonProperty("payment_id") String paymentId,
            @NotNull @Positive Long amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @JsonProperty("payment_status") String paymentStatus) {
    }
}
