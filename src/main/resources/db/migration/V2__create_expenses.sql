ALTER TABLE groups
    ADD COLUMN rotation_cursor BIGINT NOT NULL DEFAULT 0;

CREATE TABLE expenses (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id      UUID         NOT NULL REFERENCES groups (id),
    paid_by       UUID         NOT NULL REFERENCES users (id),
    description   VARCHAR(200) NOT NULL,
    amount_minor  BIGINT       NOT NULL,
    currency      CHAR(3)      NOT NULL,
    split_type    VARCHAR(20)  NOT NULL,
    expense_date  DATE         NOT NULL DEFAULT CURRENT_DATE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_expenses_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_expenses_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_expenses_description_not_blank CHECK (length(btrim(description)) > 0),
    CONSTRAINT ck_expenses_split_type CHECK (split_type IN ('EQUAL', 'PERCENTAGE', 'EXACT'))
);

CREATE INDEX idx_expenses_group_date ON expenses (group_id, expense_date DESC, created_at DESC);

CREATE TABLE expense_splits (
    id           UUID   PRIMARY KEY DEFAULT gen_random_uuid(),
    expense_id   UUID   NOT NULL REFERENCES expenses (id) ON DELETE CASCADE,
    user_id      UUID   NOT NULL REFERENCES users (id),
    amount_minor BIGINT NOT NULL,
    CONSTRAINT ck_expense_splits_amount_not_negative CHECK (amount_minor >= 0),
    CONSTRAINT uq_expense_splits_expense_user UNIQUE (expense_id, user_id)
);

CREATE INDEX idx_expense_splits_user_id ON expense_splits (user_id);
