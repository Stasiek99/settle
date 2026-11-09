CREATE TABLE settlements (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id     UUID        NOT NULL REFERENCES groups (id),
    paid_by      UUID        NOT NULL REFERENCES users (id),
    paid_to      UUID        NOT NULL REFERENCES users (id),
    amount_minor BIGINT      NOT NULL,
    currency     CHAR(3)     NOT NULL,
    settled_on   DATE        NOT NULL DEFAULT CURRENT_DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_settlements_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_settlements_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_settlements_different_users CHECK (paid_by <> paid_to)
);

CREATE INDEX idx_settlements_group_date ON settlements (group_id, settled_on DESC, created_at DESC);
