package com.estudos.cliente.api.security;

import com.estudos.cliente.api.request.InvalidRequestException;
import com.estudos.cliente.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureVerifierTest {

    private static final byte[] BODY = "{\"id\":\"ntf_1\"}".getBytes(StandardCharsets.UTF_8);
    private static final String NOW = String.valueOf(TestData.NOW.getEpochSecond());

    private final HmacSigner signer = new HmacSigner(TestData.SECRET);
    private final SignatureVerifier verifier = new SignatureVerifier(signer, Duration.ofMinutes(5),
            Clock.fixed(TestData.NOW, ZoneOffset.UTC));

    @Test
    void acceptsValidSignature() {
        assertThatCode(() -> verifier.verify(NOW, signer.signatureHeader(NOW, BODY), BODY)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTamperedBody() {
        String signature = signer.signatureHeader(NOW, BODY);

        assertThatThrownBy(() -> verifier.verify(NOW, signature, "{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void rejectsOldTimestamp() {
        String old = String.valueOf(TestData.NOW.minusSeconds(600).getEpochSecond());

        assertThatThrownBy(() -> verifier.verify(old, signer.signatureHeader(old, BODY), BODY))
                .isInstanceOf(InvalidSignatureException.class)
                .hasMessageContaining("window");
    }

    @Test
    void rejectsAnotherSecret() {
        String signature = new HmacSigner("another-secret-with-at-least-thirty-two-chars").signatureHeader(NOW, BODY);

        assertThatThrownBy(() -> verifier.verify(NOW, signature, BODY)).isInstanceOf(InvalidSignatureException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "sha256=zz", "md5=00"})
    void rejectsMalformedSignature(String signature) {
        assertThatThrownBy(() -> verifier.verify(NOW, signature, BODY)).isInstanceOf(InvalidSignatureException.class);
    }

    @Test
    void rejectsNonNumericTimestamp() {
        assertThatThrownBy(() -> verifier.verify("now", "sha256=00", BODY)).isInstanceOf(InvalidRequestException.class);
    }
}
