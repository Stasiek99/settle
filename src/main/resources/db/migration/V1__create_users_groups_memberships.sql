-- Core identity and grouping tables.
-- Passwords/credentials are intentionally absent; they arrive with auth (a later migration).

CREATE TABLE users (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(254) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Emails are unique regardless of case: "Ann@x.com" and "ann@x.com" are the same person.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

CREATE TABLE groups (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    -- ISO 4217 code. One currency per group keeps balance maths simple.
    currency   CHAR(3)      NOT NULL DEFAULT 'PLN',
    created_by UUID         NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_groups_currency_format CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_groups_created_by ON groups (created_by);

CREATE TABLE memberships (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id  UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id),
    role      VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A user can be in a given group only once.
    CONSTRAINT uq_memberships_group_user UNIQUE (group_id, user_id),
    CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'MEMBER'))
);

-- The unique constraint above already indexes lookups by group_id.
-- This one serves "which groups is this user in?".
CREATE INDEX idx_memberships_user_id ON memberships (user_id);
