package com.estudos.cliente.api.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Recebimento dos avisos da plataforma ({@code cliente.webhook.*}). Validado na inicialização.
 *
 * @param secret      segredo HMAC combinado com a plataforma
 * @param tolerance   diferença máxima aceita entre o {@code X-Timestamp} e o relógio local
 * @param maxBodySize tamanho máximo do corpo
 */
@Validated
@ConfigurationProperties(prefix = "cliente.webhook")
public record WebhookSecurityProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration tolerance,
        @NotNull DataSize maxBodySize) {

    @Override
    public String toString() {
        return "WebhookSecurityProperties[secret=***, tolerance=" + tolerance + ", maxBodySize=" + maxBodySize + "]";
    }
}
