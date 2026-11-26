package com.estudos.cliente.api.handler;

import com.estudos.cliente.api.controller.PaymentNotificationController;
import com.estudos.cliente.api.request.InvalidRequestException;
import com.estudos.cliente.api.response.ErrorResponse;
import com.estudos.cliente.api.security.InvalidSignatureException;
import com.estudos.cliente.domain.exception.StoreOrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.List;

/**
 * Converte exceções em {@link ErrorResponse}.
 *
 * <p>Para a plataforma, os códigos têm significado: {@code 4xx} é recusa definitiva (ela desiste),
 * {@code 5xx} é falha transitória (ela tenta de novo). Por isso erros inesperados viram {@code 500}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ErrorResponse> handleInvalid(InvalidRequestException ex, HttpServletRequest request) {
        return warn(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.details(), request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return warn(HttpStatus.BAD_REQUEST, "Missing required header " + ex.getHeaderName(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return warn(HttpStatus.BAD_REQUEST, "Request body is invalid", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return warn(HttpStatus.BAD_REQUEST, "Request body is missing or unreadable", List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return warn(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json", List.of(), request);
    }

    @ExceptionHandler(PaymentNotificationController.PayloadTooLargeException.class)
    ResponseEntity<ErrorResponse> handleTooLarge(RuntimeException ex, HttpServletRequest request) {
        return warn(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(InvalidSignatureException.class)
    ResponseEntity<ErrorResponse> handleSignature(InvalidSignatureException ex, HttpServletRequest request) {
        log.warn("Aviso REJEITADO (401) em {} {}: assinatura inválida ({}). Nada foi gravado.",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.UNAUTHORIZED, "Invalid notification signature", List.of(), request);
    }

    @ExceptionHandler({StoreOrderNotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        String message = ex instanceof StoreOrderNotFoundException ? ex.getMessage() : "Resource not found";
        return respond(HttpStatus.NOT_FOUND, message, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado (500) em {} {}. Se era um aviso, a plataforma vai tentar de novo.",
                request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", List.of(), request);
    }

    private ResponseEntity<ErrorResponse> warn(HttpStatus status, String message, List<String> details,
                                               HttpServletRequest request) {
        log.warn("Requisição rejeitada ({}) em {} {}: {} {}", status.value(), request.getMethod(),
                request.getRequestURI(), message, details.isEmpty() ? "" : details);
        return respond(status, message, details, request);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, List<String> details,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(clock, status, message, request.getRequestURI(), details));
    }
}
