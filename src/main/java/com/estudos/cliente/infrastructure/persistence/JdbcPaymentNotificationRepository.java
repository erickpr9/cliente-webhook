package com.estudos.cliente.infrastructure.persistence;

import com.estudos.cliente.application.dto.ReceivedNotificationView;
import com.estudos.cliente.application.port.PaymentNotificationRepository;
import com.estudos.cliente.domain.model.NotificationOutcome;
import com.estudos.cliente.domain.model.PaymentNotification;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Avisos recebidos em PostgreSQL. A idempotência vem da chave primária
 * ({@code ON CONFLICT DO NOTHING}), não de uma consulta antes do insert.
 */
@Repository
public class JdbcPaymentNotificationRepository implements PaymentNotificationRepository {

    private static final String INSERT_IF_ABSENT = """
            INSERT INTO payment_notifications (notification_id, notification_type, order_id, payment_id, amount,
                                               currency, occurred_at, payload, received_at)
            VALUES (:notificationId, :type, :orderId, :paymentId, :amount, :currency, :occurredAt,
                    CAST(:payload AS jsonb), :receivedAt)
            ON CONFLICT (notification_id) DO NOTHING
            """;

    private static final String RECORD_OUTCOME = """
            UPDATE payment_notifications SET outcome = :outcome, outcome_note = :note
             WHERE notification_id = :notificationId
            """;

    private static final String FIND_RECENT = """
            SELECT notification_id, notification_type, order_id, payment_id, amount, occurred_at, received_at,
                   outcome, outcome_note
              FROM payment_notifications
             ORDER BY received_at DESC
             LIMIT :limit
            """;

    private static final RowMapper<ReceivedNotificationView> ROW_MAPPER = (rs, rowNum) -> new ReceivedNotificationView(
            rs.getString("notification_id"),
            rs.getString("notification_type"),
            rs.getString("order_id"),
            rs.getString("payment_id"),
            rs.getLong("amount"),
            instant(rs, "occurred_at"),
            instant(rs, "received_at"),
            rs.getString("outcome"),
            rs.getString("outcome_note"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPaymentNotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertIfAbsent(PaymentNotification notification) {
        var params = new MapSqlParameterSource()
                .addValue("notificationId", notification.notificationId())
                .addValue("type", notification.type())
                .addValue("orderId", notification.orderId())
                .addValue("paymentId", notification.paymentId())
                .addValue("amount", notification.amountInCents())
                .addValue("currency", notification.currency())
                .addValue("occurredAt", toDb(notification.occurredAt()))
                .addValue("payload", notification.payload())
                .addValue("receivedAt", toDb(notification.receivedAt()));
        return jdbc.update(INSERT_IF_ABSENT, params) == 1;
    }

    @Override
    public void recordOutcome(String notificationId, NotificationOutcome outcome, String note) {
        var params = new MapSqlParameterSource()
                .addValue("notificationId", notificationId)
                .addValue("outcome", outcome.name())
                .addValue("note", note == null || note.length() <= 255 ? note : note.substring(0, 255));
        jdbc.update(RECORD_OUTCOME, params);
    }

    @Override
    public List<ReceivedNotificationView> findRecent(int limit) {
        return jdbc.query(FIND_RECENT, new MapSqlParameterSource("limit", limit), ROW_MAPPER);
    }

    static OffsetDateTime toDb(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
