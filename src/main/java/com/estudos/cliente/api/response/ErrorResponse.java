package com.estudos.cliente.api.response;

import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Formato padrão de erro.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
                            List<String> details) {

    public static ErrorResponse of(Clock clock, HttpStatus status, String message, String path, List<String> details) {
        return new ErrorResponse(clock.instant(), status.value(), status.getReasonPhrase(), message, path,
                List.copyOf(details));
    }
}
