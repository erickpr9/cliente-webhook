package com.estudos.cliente.api.security;

/**
 * Aviso não autenticado: assinatura inválida ou fora da janela de tempo. Resulta em {@code 401}.
 */
public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException(String message) {
        super(message);
    }
}
