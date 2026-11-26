-- Notificações de pagamento recebidas da plataforma. A chave primária é o id enviado pela
-- plataforma (header X-Event-Id), que se repete em todas as tentativas: é ela que garante que
-- uma reentrega não seja aplicada duas vezes.
CREATE TABLE payment_notifications (
    notification_id   VARCHAR(100) PRIMARY KEY,
    notification_type VARCHAR(50)  NOT NULL,
    order_id          VARCHAR(64)  NOT NULL,
    payment_id        VARCHAR(64)  NOT NULL,
    amount            BIGINT       NOT NULL,
    currency          CHAR(3)      NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    payload           JSONB        NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL,
    outcome           VARCHAR(20),
    outcome_note      VARCHAR(255),

    CONSTRAINT ck_payment_notifications_outcome CHECK (outcome IN ('APPLIED', 'IGNORED'))
);

CREATE INDEX ix_payment_notifications_received ON payment_notifications (received_at DESC);
CREATE INDEX ix_payment_notifications_order ON payment_notifications (order_id);
