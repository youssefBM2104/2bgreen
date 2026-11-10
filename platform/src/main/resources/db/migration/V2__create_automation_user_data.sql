-- User data of module automation (P7), inventory rows 1, 6, 7, 8 and 12.
-- Limits per user (5 rules, 10 webhooks, 5 active keys) are enforced by the services, not here.

-- Row 1 (U2): "tell me when national CO2 goes below my limit". The citizen's subscription is this row.
CREATE TABLE automation_alert_rule (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    text        NOT NULL,
    scope      text        NOT NULL DEFAULT 'national',
    -- gCO2eq/kWh
    threshold  integer     NOT NULL CHECK (threshold BETWEEN 1 AND 1000),
    active     boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX automation_alert_rule_user_idx ON automation_alert_rule (user_id);

-- Row 6 (U10): the key itself is never stored, only its SHA-256 (hex).
CREATE TABLE automation_api_key (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      text        NOT NULL,
    name         text        NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
    -- start of the key, shown in the list so the user can tell keys apart
    prefix       text        NOT NULL,
    key_hash     text        NOT NULL CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    created_at   timestamptz NOT NULL DEFAULT now(),
    revoked_at   timestamptz,
    last_used_at timestamptz,
    -- the lookup of the authentication filter
    CONSTRAINT automation_api_key_hash_unique UNIQUE (key_hash)
);
CREATE INDEX automation_api_key_user_idx ON automation_api_key (user_id);

-- Row 7 (U10): calls per key and UTC day. Follows its key (same module), so it carries no user_id.
CREATE TABLE automation_api_key_daily_counter (
    api_key_id bigint  NOT NULL REFERENCES automation_api_key (id) ON DELETE CASCADE,
    day        date    NOT NULL,
    call_count integer NOT NULL CHECK (call_count >= 0),
    PRIMARY KEY (api_key_id, day)
);
CREATE INDEX automation_api_key_daily_counter_day_idx ON automation_api_key_daily_counter (day);

-- Row 8 (U9): the DevOps subscription.
CREATE TABLE automation_webhook (
    id                   bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              text        NOT NULL,
    -- may carry a token: handle like a credential, never log it
    url                  text        NOT NULL,
    scope                text        NOT NULL DEFAULT 'national',
    condition_type       text        NOT NULL,
    threshold            integer     CHECK (threshold BETWEEN 1 AND 1000),
    -- HMAC-SHA256 needs the secret itself, so it cannot be hashed like an API key
    secret               text        NOT NULL,
    active               boolean     NOT NULL DEFAULT true,
    consecutive_failures integer     NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    created_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT automation_webhook_condition_check CHECK (condition_type IN ('BELOW', 'ABOVE', 'BAND_CHANGED')),
    -- a threshold condition needs a threshold, a colour change has none
    CONSTRAINT automation_webhook_threshold_presence_check CHECK ((condition_type = 'BAND_CHANGED') = (threshold IS NULL))
);
CREATE INDEX automation_webhook_user_idx ON automation_webhook (user_id);

-- Row 12 (P8, U16): memory of the rule engine, one row per thing it follows. subject_id is a plain reference:
-- a target lives in module regional, so its state is removed on the TargetDeleted event, not by a foreign key.
CREATE TABLE automation_rule_state (
    id               bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          text        NOT NULL,
    subject_type     text        NOT NULL,
    subject_id       bigint      NOT NULL,
    -- which side of the condition the subject was on at the last evaluation; the values belong to P8
    state            text        NOT NULL,
    last_crossing_at timestamptz,
    -- observedAt of the last snapshot evaluated: a replayed event is recognised and ignored
    last_observed_at timestamptz,
    CONSTRAINT automation_rule_state_subject_check CHECK (subject_type IN ('ALERT_RULE', 'WEBHOOK', 'TARGET')),
    CONSTRAINT automation_rule_state_subject_unique UNIQUE (subject_type, subject_id)
);
CREATE INDEX automation_rule_state_user_idx ON automation_rule_state (user_id);

COMMENT ON TABLE automation_alert_rule IS 'Clean-grid alert rules of citizens (U2). Personal data. Kept until the user deletes it.';
COMMENT ON TABLE automation_api_key IS 'API keys of DevOps users (U10), hashed. Personal data. A revoked key is kept 30 days for audit.';
COMMENT ON TABLE automation_api_key_daily_counter IS 'Calls per API key and UTC day (U10). Kept 13 months.';
COMMENT ON TABLE automation_webhook IS 'Conditional webhooks of DevOps users (U9). Personal data, url and secret are credentials.';
COMMENT ON TABLE automation_rule_state IS 'State of the rule engine per rule, webhook or target (P8). Deleted with what it follows.';
