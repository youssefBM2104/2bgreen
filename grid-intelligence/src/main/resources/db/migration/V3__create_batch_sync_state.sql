-- What the batch remembers between two runs (G3): for each UTC year, how many rows ODRE held per quality when the
-- year was last loaded. The daily run asks ODRE for the same counts in one call and reloads only the years whose
-- counts differ, which covers both new rows and rows moving from consolidated to definitive.
-- Not business data: deleting the table only makes the next run reload every year, nothing is lost.
CREATE TABLE batch_sync_state (
    scope      text        NOT NULL,
    year       integer     NOT NULL,
    quality    text        NOT NULL,
    row_count  bigint      NOT NULL CHECK (row_count >= 0),
    loaded_at  timestamptz NOT NULL DEFAULT now(),
    -- a UTC year can hold two qualities: quality flips on Paris years (see YearQualityCount)
    PRIMARY KEY (scope, year, quality),
    CONSTRAINT batch_sync_state_quality_check CHECK (quality IN ('CONSOLIDATED', 'DEFINITIVE'))
);

COMMENT ON TABLE batch_sync_state IS 'Batch layer bookkeeping: row counts per UTC year and quality at the last load. Written only by the batch.';
COMMENT ON COLUMN batch_sync_state.year IS 'UTC year of date_heure.';
COMMENT ON COLUMN batch_sync_state.row_count IS 'Rows ODRE counted for that year and quality, empty placeholders included. Only compared with the same count from ODRE.';
