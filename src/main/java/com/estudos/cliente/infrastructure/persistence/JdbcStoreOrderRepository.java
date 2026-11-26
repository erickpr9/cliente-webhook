package com.estudos.cliente.infrastructure.persistence;

import com.estudos.cliente.application.exception.ConcurrentUpdateException;
import com.estudos.cliente.application.port.StoreOrderRepository;
import com.estudos.cliente.domain.model.FulfillmentStatus;
import com.estudos.cliente.domain.model.StoreOrder;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

import static com.estudos.cliente.infrastructure.persistence.JdbcPaymentNotificationRepository.instant;
import static com.estudos.cliente.infrastructure.persistence.JdbcPaymentNotificationRepository.toDb;

/**
 * Pedidos da loja em PostgreSQL, com lock otimista pela coluna {@code version}.
 */
@Repository
public class JdbcStoreOrderRepository implements StoreOrderRepository {

    private static final String FIND_BY_ID = """
            SELECT id, payment_id, amount, currency, status, last_notification_at, version
              FROM store_orders WHERE id = :id
            """;

    private static final String INSERT = """
            INSERT INTO store_orders (id, payment_id, amount, currency, status, last_notification_at,
                                      created_at, updated_at, version)
            VALUES (:id, :paymentId, :amount, :currency, :status, :lastNotificationAt, :now, :now, 0)
            """;

    private static final String UPDATE = """
            UPDATE store_orders
               SET status = :status, last_notification_at = :lastNotificationAt, updated_at = :now,
                   version = version + 1
             WHERE id = :id AND version = :version
            """;

    private static final RowMapper<StoreOrder> ROW_MAPPER = (rs, rowNum) -> StoreOrder.restore(
            rs.getString("id"),
            rs.getString("payment_id"),
            rs.getLong("amount"),
            rs.getString("currency"),
            FulfillmentStatus.valueOf(rs.getString("status")),
            instant(rs, "last_notification_at"),
            rs.getLong("version"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcStoreOrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StoreOrder> findById(String id) {
        return jdbc.query(FIND_BY_ID, new MapSqlParameterSource("id", id), ROW_MAPPER).stream().findFirst();
    }

    @Override
    public void insert(StoreOrder order, Instant now) {
        var params = new MapSqlParameterSource()
                .addValue("id", order.id())
                .addValue("paymentId", order.paymentId())
                .addValue("amount", order.amountInCents())
                .addValue("currency", order.currency())
                .addValue("status", order.status().name())
                .addValue("lastNotificationAt", toDb(order.lastNotificationAt().orElse(null)))
                .addValue("now", toDb(now));
        jdbc.update(INSERT, params);
    }

    @Override
    public void update(StoreOrder order, Instant now) {
        var params = new MapSqlParameterSource()
                .addValue("id", order.id())
                .addValue("status", order.status().name())
                .addValue("lastNotificationAt", toDb(order.lastNotificationAt().orElse(null)))
                .addValue("now", toDb(now))
                .addValue("version", order.version());
        if (jdbc.update(UPDATE, params) != 1) {
            throw new ConcurrentUpdateException(order.id());
        }
    }
}
