-- Enums
CREATE TYPE wallet_type AS ENUM ('SAVINGS', 'CURRENT', 'ESCROW', 'REWARD');
CREATE TYPE wallet_status AS ENUM ('ACTIVE', 'FROZEN', 'CLOSED');
CREATE TYPE account_status AS ENUM ('ACTIVE', 'SUSPENDED', 'DEACTIVATED');
CREATE TYPE transaction_type AS ENUM ('CREDIT', 'DEBIT', 'REFUND', 'REVERSAL');
CREATE TYPE saga_status AS ENUM ('STARTED', 'RUNNING', 'COMPENSATING', 'COMPLETED', 'FAILED', 'COMPENSATED', 'POISON');
CREATE TYPE step_status AS ENUM ('PENDING', 'EXECUTING', 'COMPLETED', 'FAILED', 'COMPENSATED', 'SKIPPED');

-- Account
CREATE TABLE IF NOT EXISTS account (
    id              VARCHAR(64)    PRIMARY KEY,
    user_name       VARCHAR(128)   NOT NULL,
    email           VARCHAR(256)   NOT NULL UNIQUE,
    contact_number  VARCHAR(15)    NOT NULL UNIQUE,
    status          account_status NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Wallet
CREATE TABLE IF NOT EXISTS wallet (
    id          BIGINT        PRIMARY KEY,
    type        wallet_type   NOT NULL,
    balance     DECIMAL(20,8) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    account_id  VARCHAR(64)   NOT NULL REFERENCES account(id),
    currency    VARCHAR(10)   NOT NULL DEFAULT 'INR',
    status      wallet_status NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (account_id, currency, type)
);
CREATE INDEX IF NOT EXISTS idx_wallet_account_id ON wallet(account_id);

-- Transaction (immutable ledger)
CREATE TABLE IF NOT EXISTS transaction (
    transaction_id  BIGINT           PRIMARY KEY,
    type            transaction_type NOT NULL,
    wallet_id       BIGINT           NOT NULL REFERENCES wallet(id),
    account_id      VARCHAR(64)      NOT NULL,
    amount          DECIMAL(20,8)    NOT NULL CHECK (amount > 0),
    saga_id         BIGINT           NOT NULL,
    idempotency_key VARCHAR(128)     NOT NULL UNIQUE,
    details         JSONB,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_txn_wallet_id  ON transaction(wallet_id);
CREATE INDEX IF NOT EXISTS idx_txn_saga_id    ON transaction(saga_id);
CREATE INDEX IF NOT EXISTS idx_txn_account_id ON transaction(account_id);

-- SAGA Instance
CREATE TABLE IF NOT EXISTS saga_instance (
    id                BIGINT      PRIMARY KEY,
    saga_type         VARCHAR(64) NOT NULL,
    status            saga_status NOT NULL DEFAULT 'STARTED',
    current_step      INT         NOT NULL DEFAULT 0,
    total_steps       INT         NOT NULL,
    payload           JSONB       NOT NULL,
    owner_instance_id VARCHAR(64) NOT NULL,
    last_heartbeat    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    retry_count       INT         NOT NULL DEFAULT 0,
    max_retries       INT         NOT NULL DEFAULT 3,
    error_message     TEXT,
    initiated_by      VARCHAR(64) NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_saga_initiated_by ON saga_instance(initiated_by);
CREATE INDEX IF NOT EXISTS idx_saga_status       ON saga_instance(status);

-- SAGA Step Execution
CREATE TABLE IF NOT EXISTS saga_step_execution (
    id           BIGINT      PRIMARY KEY,
    saga_id      BIGINT      NOT NULL REFERENCES saga_instance(id),
    step_index   INT         NOT NULL,
    step_name    VARCHAR(64) NOT NULL,
    step_type    VARCHAR(20) NOT NULL CHECK (step_type IN ('FORWARD', 'COMPENSATE')),
    status       step_status NOT NULL DEFAULT 'PENDING',
    input_data   JSONB,
    output_data  JSONB,
    error_detail TEXT,
    initiated_by VARCHAR(64) NOT NULL,
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (saga_id, step_index, step_type)
);
CREATE INDEX IF NOT EXISTS idx_saga_step_saga_id ON saga_step_execution(saga_id);

-- Transaction Intent (idempotency + async tracking)
CREATE TYPE intent_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'COMPENSATED');

CREATE TABLE IF NOT EXISTS transaction_intent (
    intent_id        VARCHAR(128) PRIMARY KEY,
    source_wallet_id BIGINT       NOT NULL,
    dest_wallet_id   BIGINT       NOT NULL,
    amount           DECIMAL(20,8) NOT NULL CHECK (amount > 0),
    initiated_by     VARCHAR(64)  NOT NULL,
    status           intent_status NOT NULL DEFAULT 'PENDING',
    saga_id          BIGINT,
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intent_initiated_by ON transaction_intent(initiated_by);
