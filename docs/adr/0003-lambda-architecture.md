# ADR-003: A simplified Lambda architecture, two tables merged in one view

## Status

Accepted (2026-10-14).

## Context

2026-10-14. ODRÉ publishes the same measures twice:

- **real time** (`eco2mix-*-tr`): every 15 minutes, provisional, only since 2026-09-01;
- **consolidated then definitive** (`eco2mix-*-cons-def`): 30-minute values, national since 2012 and regional since
  2013, months late, and their quality changes in place (consolidated becomes definitive a year later).

The features need both: the current value (traffic light, alerts, API) and years of history (trends, typical
profile, targets).

## Options considered

1. **Real time only.** Simple, but the history would start on 2026-09-01 and stay provisional forever.
2. **One table written by both paths**, the batch overwriting provisional rows. Two writers on the same rows: their
   order matters, a late provisional row can overwrite a definitive one, and the grains differ (15 vs 30 min).
3. **Kappa: everything through Kafka**, the history replayed as events. About 257,000 national rows plus about
   210,000 regional rows per year through a log built for a few messages per 15 minutes, and the quality updates of
   cons-def to model as events. Much more work for no feature.
4. **Lambda: two paths, two tables, one read view (chosen).**

## Decision

Option 4.

| Path | Source | Writer | Table | Grain |
|---|---|---|---|---|
| Speed | `*-tr` | Kafka Streams (`stream` profile) | `grid.speed_snapshot` | 15 min, `PROVISIONAL` |
| Batch | `*-cons-def` | daily job (`batch` profile) | `grid.batch_snapshot` | 30 min, `CONSOLIDATED` or `DEFINITIVE` |

Each table has exactly one writer. The view `grid.merged_snapshot` serves the history: for a given half hour the batch
row wins, otherwise the two provisional quarter-hours are averaged into one 30-minute row (`docs/data/merged-view.md`).
The batch does not use Kafka; it downloads only the years whose row counts changed and upserts them without ever
downgrading a definitive row.

## Consequences

- Good: no write conflict by construction. The speed table can be pruned, the batch table rebuilt from ODRÉ.
- Good: one read model; the read API and `platform` do not know which path a row came from, only its `quality`.
- Bad: the 30-minute average of provisional rows is a choice; whether cons-def is a half-hour mean could not be checked
  yet (no overlap between the datasets so far).
- Bad: the "latest" endpoint reads `speed_snapshot` directly, for 15-minute freshness; only ranges use the view.
- Bad: a quality upgrade by the batch produces no event, so nothing reacts to it.
- Limit: today the batch mostly copies cons-def. The real pre-computation (typical CO2 by hour and weekday) is
  not built yet. The first regional load takes about 2 hours (ODRÉ sends a regional year in about 18 minutes).
