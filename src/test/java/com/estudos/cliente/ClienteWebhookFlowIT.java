package com.estudos.cliente;

import com.estudos.cliente.api.controller.PaymentNotificationController;
import com.estudos.cliente.api.security.HmacSigner;
import com.estudos.cliente.support.PostgresIntegrationTest;
import com.estudos.cliente.support.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loja de ponta a ponta: aviso assinado por HTTP → registro → pedido liberado, incluindo
 * reentregas, avisos fora de ordem e as falhas simuladas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "cliente.simulation.enabled=true",
        "cliente.simulation.slow-delay=300ms"
})
class ClienteWebhookFlowIT extends PostgresIntegrationTest {

    private static final String URL = PaymentNotificationController.PATH;

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ObjectMapper objectMapper;

    private final HmacSigner signer = new HmacSigner(TestData.SECRET);

    @AfterEach
    void resetFailureMode() {
        changeFailureMode("NONE");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<String> send(String notificationId, String body) {
        return send(notificationId, body.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON, true);
    }

    private ResponseEntity<String> send(String notificationId, byte[] body, MediaType contentType, boolean sign) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.set(PaymentNotificationController.EVENT_ID_HEADER, notificationId);
        headers.set(PaymentNotificationController.TIMESTAMP_HEADER, timestamp);
        headers.set(PaymentNotificationController.SIGNATURE_HEADER,
                sign ? signer.signatureHeader(timestamp, body) : "sha256=" + "0".repeat(64));
        return http.exchange(URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> notify(String id, String type, Instant occurredAt, String orderId) {
        return send(id, TestData.payload(id, type, occurredAt, orderId, "pay_" + orderId, 15990));
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode order(String orderId) throws Exception {
        return json(http.getForEntity("/orders/" + orderId, String.class));
    }

    private void changeFailureMode(String mode) {
        http.exchange("/simulation/failure-mode", HttpMethod.PUT, new HttpEntity<>(Map.of("mode", mode)), String.class);
    }

    private int storedNotifications() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM payment_notifications", Integer.class);
        return count == null ? 0 : count;
    }

    // ── Cenários ────────────────────────────────────────────────────────────

    @Test
    void confirmationReleasesTheOrderForShipping() throws Exception {
        ResponseEntity<String> response = notify("ntf_ok", "payment.confirmed", Instant.now(), "ord_a");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).path("status").asText()).isEqualTo("processed");
        JsonNode order = order("ord_a");
        assertThat(order.path("status").asText()).isEqualTo("READY_TO_SHIP");
        assertThat(order.path("readyToShip").asBoolean()).isTrue();
    }

    @Test
    void redeliveryIsAcknowledgedOnce() throws Exception {
        notify("ntf_dup", "payment.confirmed", Instant.now(), "ord_b");

        ResponseEntity<String> again = notify("ntf_dup", "payment.confirmed", Instant.now(), "ord_b");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(again).path("status").asText()).isEqualTo("duplicate");
        assertThat(storedNotifications()).isEqualTo(1);
    }

    @Test
    void simultaneousRedeliveriesAreProcessedOnce() throws Exception {
        String body = TestData.payload("ntf_burst", "payment.confirmed", Instant.now(), "ord_c", "pay_ord_c", 15990);
        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<String> statuses = new ArrayList<>();
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return send("ntf_burst", body);
                }));
            }
            start.countDown();
            for (Future<ResponseEntity<String>> future : futures) {
                statuses.add(json(future.get()).path("status").asText());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).filteredOn("processed"::equals).hasSize(1);
        assertThat(statuses).filteredOn("duplicate"::equals).hasSize(requests - 1);
    }

    @Test
    void refundAfterConfirmationCancelsShipping() throws Exception {
        Instant confirmedAt = Instant.now().minusSeconds(10);
        notify("ntf_c", "payment.confirmed", confirmedAt, "ord_d");

        notify("ntf_r", "payment.refunded", confirmedAt.plusSeconds(5), "ord_d");

        assertThat(order("ord_d").path("status").asText()).isEqualTo("REFUNDED");
    }

    @Test
    void outOfOrderNotificationIsIgnored() throws Exception {
        Instant confirmedAt = Instant.now();
        notify("ntf_new", "payment.confirmed", confirmedAt, "ord_e");

        ResponseEntity<String> late = notify("ntf_old", "payment.refused", confirmedAt.minusSeconds(60), "ord_e");

        assertThat(json(late).path("status").asText()).isEqualTo("ignored");
        assertThat(json(late).path("note").asText()).contains("STALE");
        assertThat(order("ord_e").path("status").asText()).isEqualTo("READY_TO_SHIP");
    }

    @Test
    void unknownTypeIsRecordedAndIgnored() throws Exception {
        ResponseEntity<String> response = notify("ntf_other", "customer.created", Instant.now(), "ord_f");

        assertThat(json(response).path("status").asText()).isEqualTo("ignored");
        JsonNode list = json(http.getForEntity("/notifications?limit=5", String.class));
        assertThat(list.path(0).path("outcome").asText()).isEqualTo("IGNORED");
        assertThat(http.getForEntity("/orders/ord_f", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidSignatureIsRejectedAndNotStored() {
        byte[] body = TestData.payload("ntf_forged", "payment.confirmed", Instant.now()).getBytes(StandardCharsets.UTF_8);

        ResponseEntity<String> response = send("ntf_forged", body, MediaType.APPLICATION_JSON, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(storedNotifications()).isZero();
    }

    @Test
    void malformedRequestsAreRejected() throws Exception {
        assertThat(send("ntf_x", "{broken").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> invalid = send("ntf_x", "{\"id\":\"ntf_x\",\"type\":\"payment.confirmed\"}");
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json(invalid).path("details").toString()).contains("occurredAt").contains("data");

        ResponseEntity<String> mismatch = send("ntf_header",
                TestData.payload("ntf_body", "payment.confirmed", Instant.now()));
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        byte[] huge = ("{\"id\":\"" + "x".repeat(70_000) + "\"}").getBytes(StandardCharsets.UTF_8);
        assertThat(send("ntf_big", huge, MediaType.APPLICATION_JSON, true).getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        assertThat(send("ntf_txt", "x".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN, true).getStatusCode())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        HttpHeaders noHeaders = new HttpHeaders();
        noHeaders.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.exchange(URL, HttpMethod.POST, new HttpEntity<>("{}", noHeaders), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(storedNotifications()).isZero();
    }

    @Test
    void unavailableStoreAnswers503WithoutStoring() throws Exception {
        changeFailureMode("UNAVAILABLE");

        ResponseEntity<String> response = notify("ntf_down", "payment.confirmed", Instant.now(), "ord_g");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(storedNotifications()).isZero();
        assertThat(json(http.getForEntity("/simulation/failure-mode", String.class)).path("mode").asText())
                .isEqualTo("UNAVAILABLE");
    }

    @Test
    void rejectingStoreAnswers410() {
        changeFailureMode("REJECT");

        assertThat(notify("ntf_gone", "payment.confirmed", Instant.now(), "ord_h").getStatusCode())
                .isEqualTo(HttpStatus.GONE);
    }

    @Test
    void slowStoreStillProcessesTheNotification() throws Exception {
        changeFailureMode("SLOW");

        ResponseEntity<String> response = notify("ntf_slow", "payment.confirmed", Instant.now(), "ord_i");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(order("ord_i").path("status").asText()).isEqualTo("READY_TO_SHIP");
    }

    @Test
    void invalidFailureModeIsRejected() {
        ResponseEntity<String> response = http.exchange("/simulation/failure-mode", HttpMethod.PUT,
                new HttpEntity<>(Map.of("mode", "BROKEN")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void healthIsUp() throws Exception {
        assertThat(json(http.getForEntity("/actuator/health", String.class)).path("status").asText()).isEqualTo("UP");
        assertThat(http.getForEntity("/unknown/" + UUID.randomUUID(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
