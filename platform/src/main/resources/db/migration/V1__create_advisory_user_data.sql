-- User data of module advisory (P7), from the inventory docs/data/user-data.md, rows 3 to 5.
-- Tables of a module are prefixed with its name. No foreign key leaves the module.
-- user_id is the "sub" claim of the Keycloak token: the only thing platform knows about a person.

-- Row 3 (U4): the usages a citizen wants a best slot for.
CREATE TABLE advisory_usage (
    id               bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          text        NOT NULL,
    label            text        NOT NULL CHECK (length(label) BETWEEN 1 AND 60),
    duration_minutes integer     NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
    -- "must be done before", Paris local time of day: a stored usage recurs, so it is not a date
    finish_before    time,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT advisory_usage_label_unique UNIQUE (user_id, label)
);

-- Row 4 (U6): what the citizen actually ran. No CO2 value: it is computed at read time from grid data.
CREATE TABLE advisory_usage_log_entry (
    id               bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          text        NOT NULL,
    -- a copy of the label, not a reference: deleting a usage keeps the log readable
    usage_label      text        NOT NULL CHECK (length(usage_label) BETWEEN 1 AND 60),
    started_at       timestamptz NOT NULL,
    duration_minutes integer     NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
    created_at       timestamptz NOT NULL DEFAULT now()
);
-- the list and the weekly summary of one user; started_at alone serves the 24-month purge
CREATE INDEX advisory_usage_log_entry_user_idx ON advisory_usage_log_entry (user_id, started_at);
CREATE INDEX advisory_usage_log_entry_started_idx ON advisory_usage_log_entry (started_at);

-- Row 5 (U3, proposed): the region a citizen picked. Only the INSEE code, never a location.
CREATE TABLE advisory_region_preference (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     text        NOT NULL,
    region_code text        NOT NULL CHECK (region_code ~ '^[0-9]{2}$'),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT advisory_region_preference_user_unique UNIQUE (user_id)
);

COMMENT ON TABLE advisory_usage IS 'Usages chosen by a citizen (U4). Personal data. Kept until the user deletes it.';
COMMENT ON TABLE advisory_usage_log_entry IS 'Usage log of a citizen (U6). Personal data. Kept 24 months.';
COMMENT ON TABLE advisory_region_preference IS 'Region picked by a citizen (U3). Personal data. Kept until changed or deleted.';
