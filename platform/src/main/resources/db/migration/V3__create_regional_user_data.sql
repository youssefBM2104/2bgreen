-- User data of module regional (P7), inventory rows 10 and 11.
-- The official's own region is not here: it is a Keycloak attribute read from the token (U11).

-- Row 10 (U13)
CREATE TABLE regional_target (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- the author
    user_id         text        NOT NULL,
    region_code     text        NOT NULL CHECK (region_code ~ '^[0-9]{2}$'),
    indicator       text        NOT NULL,
    reference_year  integer     NOT NULL CHECK (reference_year BETWEEN 2013 AND 2100),
    -- computed once, when the target is created
    reference_value numeric     NOT NULL,
    target_value    numeric     NOT NULL,
    deadline        date        NOT NULL,
    -- in points; null means the pace alert (U16) is off for this target
    tolerance       numeric     CHECK (tolerance > 0),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT regional_target_indicator_check
        CHECK (indicator IN ('RENEWABLE_SHARE', 'COVERAGE_RATE', 'CONSUMPTION_DROP', 'ESTIMATED_CO2'))
);
CREATE INDEX regional_target_user_idx ON regional_target (user_id);
CREATE INDEX regional_target_region_idx ON regional_target (region_code);

-- Row 11 (U13): one row per target and day, written by the daily recalculation. Follows its target (same module).
CREATE TABLE regional_target_progress (
    id        bigint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_id bigint  NOT NULL REFERENCES regional_target (id) ON DELETE CASCADE,
    day       date    NOT NULL,
    value     numeric NOT NULL,
    status    text    NOT NULL,
    CONSTRAINT regional_target_progress_status_check CHECK (status IN ('AHEAD', 'ON_TRACK', 'LATE')),
    -- running the recalculation twice the same day must not add a second row
    CONSTRAINT regional_target_progress_day_unique UNIQUE (target_id, day)
);

COMMENT ON TABLE regional_target IS 'Targets set by regional officials (U13). The author is personal data. Kept until deleted.';
COMMENT ON TABLE regional_target_progress IS 'Daily progress of a target (U13). Deleted with its target.';
