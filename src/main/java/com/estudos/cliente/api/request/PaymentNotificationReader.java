package com.estudos.cliente.api.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Converte o corpo cru em {@link PaymentNotificationRequest} e aplica a Bean Validation.
 *
 * <p>O parse é manual porque a assinatura é conferida antes, sobre os bytes exatos recebidos.
 */
public final class PaymentNotificationReader {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public PaymentNotificationReader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.validator = Objects.requireNonNull(validator);
    }

    public PaymentNotificationRequest read(byte[] body) {
        PaymentNotificationRequest request;
        try {
            request = objectMapper.readValue(body, PaymentNotificationRequest.class);
        } catch (IOException e) {
            throw new InvalidRequestException("Request body is not a valid notification JSON payload");
        }
        if (request == null) {
            throw new InvalidRequestException("Request body must not be empty");
        }
        List<String> violations = validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .toList();
        if (!violations.isEmpty()) {
            throw new InvalidRequestException("Notification payload is invalid", violations);
        }
        return request;
    }
}
