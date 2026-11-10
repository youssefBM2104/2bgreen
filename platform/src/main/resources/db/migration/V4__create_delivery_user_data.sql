-- User data of module delivery (P7), inventory rows 2 and 9: one table per channel.
-- source_id and webhook_id point at rows of module automation or regional: plain references, no foreign key.

-- Row 2 (U2, U16): the in-app channel.
CREATE TABLE delivery_notification (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    text        NOT NULL,
    type       text        NOT NULL,
    -- the alert rule (CLEAN_GRID) or the target (TARGET_PACE) that caused it
    source_id  bigint      NOT NULL,
    message    text        NOT NULL CHECK (length(message) BETWEEN 1 AND 500),
    created_at timestamptz NOT NULL DEFAULT now(),
    -- null while unread
    read_at    timestamptz,
    CONSTRAINT delivery_notification_type_check CHECK (type IN ('CLEAN_GRID', 'TARGET_PACE'))
);
CREATE INDEX delivery_notification_user_idx ON delivery_notification (user_id, created_at DESC);
CREATE INDEX delivery_notification_created_idx ON delivery_notification (created_at);

-- Row 9 (U9): one row per attempt of an HTTP call. Never the payload nor the response body.
CREATE TABLE delivery_webhook_delivery (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- sent as X-2bGreen-Delivery; the attempts of one delivery share it so the receiver can deduplicate
    delivery_id  uuid        NOT NULL,
    -- copied from the webhook: the log outlives it and must stay reachable for a purge
    user_id      text        NOT NULL,
    webhook_id   bigint      NOT NULL,
    attempt      integer     NOT NULL CHECK (attempt >= 1),
    attempted_at timestamptz NOT NULL DEFAULT now(),
    outcome      text        NOT NULL,
    http_status  integer     CHECK (http_status BETWEEN 100 AND 599),
    -- when there is no HTTP answer: timeout, refused connection, forbidden address...
    error_kind   text,
    CONSTRAINT delivery_webhook_delivery_outcome_check CHECK (outcome IN ('SUCCESS', 'FAILED')),
    CONSTRAINT delivery_webhook_delivery_attempt_unique UNIQUE (delivery_id, attempt)
);
CREATE INDEX delivery_webhook_delivery_webhook_idx ON delivery_webhook_delivery (webhook_id, attempted_at DESC);
CREATE INDEX delivery_webhook_delivery_user_idx ON delivery_webhook_delivery (user_id);
CREATE INDEX delivery_webhook_delivery_attempted_idx ON delivery_webhook_delivery (attempted_at);

COMMENT ON TABLE delivery_notification IS 'In-app notifications (U2, U16). Personal data. Kept 90 days.';
COMMENT ON TABLE delivery_webhook_delivery IS 'Log of webhook call attempts (U9). Personal data. Kept 90 days.';
