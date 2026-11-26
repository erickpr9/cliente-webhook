-- Pedidos da loja, do ponto de vista da expedição. O status muda conforme os avisos de
-- pagamento: só pedido READY_TO_SHIP pode ser separado e enviado.
CREATE TABLE store_orders (
    id                   VARCHAR(64) PRIMARY KEY,
    payment_id           VARCHAR(64) NOT NULL,
    amount               BIGINT      NOT NULL,
    currency             CHAR(3)     NOT NULL,
    status               VARCHAR(20) NOT NULL,
    -- Instante do último aviso aplicado; usado para descartar avisos fora de ordem.
    last_notification_at TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_store_orders_status CHECK (
        status IN ('AWAITING_PAYMENT', 'READY_TO_SHIP', 'PAYMENT_FAILED', 'REFUNDED')
    ),
    CONSTRAINT ck_store_orders_amount CHECK (amount > 0)
);
