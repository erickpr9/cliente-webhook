package com.estudos.cliente.api.security;

import com.estudos.cliente.api.request.InvalidRequestException;

import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Confere se o aviso veio mesmo da plataforma: janela de tempo (anti-replay) e assinatura HMAC
 * de {@code "{timestamp}.{body}"}, comparada em tempo constante.
 *
 * <p>Sem essa verificação, qualquer um que descobrisse a URL poderia "confirmar" pagamentos e
 * fazer a loja enviar mercadoria sem receber.
 */
public final class SignatureVerifier {

    private final HmacSigner signer;
    private final Duration tolerance;
    private final Clock clock;

    public SignatureVerifier(HmacSigner signer, Duration tolerance, Clock clock) {
        this.signer = Objects.requireNonNull(signer);
        this.tolerance = Objects.requireNonNull(tolerance);
        this.clock = Objects.requireNonNull(clock);
    }

    public void verify(String timestampHeader, String signatureHeader, byte[] body) {
        Instant sentAt;
        try {
            sentAt = Instant.ofEpochSecond(Long.parseLong(timestampHeader.trim()));
        } catch (RuntimeException e) {
            throw new InvalidRequestException("X-Timestamp must be the epoch time in seconds");
        }
        if (Duration.between(sentAt, clock.instant()).abs().compareTo(tolerance) > 0) {
            throw new InvalidSignatureException("Timestamp is outside the accepted window");
        }
        if (!signatureHeader.startsWith(HmacSigner.SIGNATURE_PREFIX)) {
            throw new InvalidSignatureException("Signature must use the format sha256=<hex>");
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(HmacSigner.SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new InvalidSignatureException("Signature must use the format sha256=<hex>");
        }
        if (!MessageDigest.isEqual(signer.sign(timestampHeader, body), provided)) {
            throw new InvalidSignatureException("Signature does not match");
        }
    }
}
