package com.estudos.cliente.api.request;

import java.util.List;

/**
 * Aviso mal formado. Resulta em {@code 400}, que a plataforma trata como recusa definitiva.
 */
public class InvalidRequestException extends RuntimeException {

    private final List<String> details;

    public InvalidRequestException(String message) {
        this(message, List.of());
    }

    public InvalidRequestException(String message, List<String> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    public List<String> details() {
        return details;
    }
}
