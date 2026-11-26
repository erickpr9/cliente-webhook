package com.estudos.cliente.api.controller;

import com.estudos.cliente.api.request.InvalidRequestException;
import com.estudos.cliente.api.request.PaymentNotificationReader;
import com.estudos.cliente.api.request.PaymentNotificationRequest;
import com.estudos.cliente.api.response.NotificationReceivedResponse;
import com.estudos.cliente.api.security.SignatureVerifier;
import com.estudos.cliente.api.security.WebhookSecurityProperties;
import com.estudos.cliente.application.dto.ReceivePaymentNotificationCommand;
import com.estudos.cliente.application.usecase.ReceivePaymentNotificationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Endpoint que a plataforma de pagamento chama quando um pagamento muda de estado.
 *
 * <p>Ordem: tamanho, assinatura, JSON e, por fim, o tratamento. Responde {@code 200} sempre que
 * o aviso foi registrado, mesmo quando ele não muda nada; erros {@code 5xx} fazem a plataforma
 * reenviar.
 */
@RestController
@Tag(name = "Payment notifications", description = "Avisos de pagamento enviados pela plataforma")
public class PaymentNotificationController {

    public static final String PATH = "/webhooks/payment-notifications";
    public static final String EVENT_ID_HEADER = "X-Event-Id";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Signature";

    private final SignatureVerifier signatureVerifier;
    private final PaymentNotificationReader reader;
    private final ReceivePaymentNotificationUseCase receiveNotification;
    private final long maxBodyBytes;

    public PaymentNotificationController(SignatureVerifier signatureVerifier, PaymentNotificationReader reader,
                                         ReceivePaymentNotificationUseCase receiveNotification,
                                         WebhookSecurityProperties properties) {
        this.signatureVerifier = signatureVerifier;
        this.reader = reader;
        this.receiveNotification = receiveNotification;
        this.maxBodyBytes = properties.maxBodySize().toBytes();
    }

    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recebe um aviso de pagamento assinado com HMAC-SHA256")
    public NotificationReceivedResponse receive(@RequestHeader(EVENT_ID_HEADER) String eventId,
                                                @RequestHeader(TIMESTAMP_HEADER) String timestamp,
                                                @RequestHeader(SIGNATURE_HEADER) String signature,
                                                @RequestBody byte[] body) {
        MDC.put("notificationId", eventId);
        try {
            if (body.length > maxBodyBytes) {
                throw new PayloadTooLargeException(maxBodyBytes);
            }
            signatureVerifier.verify(timestamp, signature, body);

            PaymentNotificationRequest request = reader.read(body);
            if (!request.id().equals(eventId)) {
                throw new InvalidRequestException(EVENT_ID_HEADER + " does not match the payload id");
            }

            var data = request.data();
            return NotificationReceivedResponse.from(receiveNotification.execute(new ReceivePaymentNotificationCommand(
                    request.id(), request.type(), data.orderId(), data.paymentId(), data.amount(), data.currency(),
                    request.occurredAt(), new String(body, StandardCharsets.UTF_8))));
        } finally {
            MDC.remove("notificationId");
        }
    }

    /**
     * Corpo acima do limite configurado. Resulta em {@code 413}.
     */
    public static class PayloadTooLargeException extends RuntimeException {

        PayloadTooLargeException(long maxBytes) {
            super("Request body exceeds the limit of " + maxBytes + " bytes");
        }
    }
}
