-- SRS sections 7 and 13, Phase 1. Identity behavior arrives in Phase 2.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_email_not_blank CHECK (length(trim(email)) > 0),
    CONSTRAINT users_password_hash_not_blank CHECK (length(trim(password_hash)) > 0),
    CONSTRAINT users_timezone_not_blank CHECK (length(trim(timezone)) > 0)
);
CREATE UNIQUE INDEX users_email_unique ON users (lower(email));
