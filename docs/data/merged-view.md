# Merged read view (G4)

View `grid.merged_snapshot`, created by migration `V4__create_merged_snapshot_view.sql`. It is the read side of the
Lambda architecture: one series at **30-min grain** over `speed_snapshot` (provisional, 15 min) and `batch_snapshot`
(consolidated or definitive, 30 min). Plain view, not materialized: always current, nothing to refresh.

## Rules

1. **Batch wins.** For a given `(scope, observed_at)` at 30-min grain, if `batch_snapshot` has a row, that row is
   returned and the provisional quarter-hours of that half hour are ignored.
2. **Otherwise provisional.** The quarter-hours of the half hour (`:00`+`:15`, `:30`+`:45`) are combined into one row at
   the bucket start (`time_bucket('30 minutes', observed_at)`, aligned on UTC, which matches the `:00`/`:30` grid of
   cons-def because Paris offsets are whole hours).
3. **Quality is derived.** `PROVISIONAL` for the combined rows, the row's own `CONSOLIDATED` / `DEFINITIVE` for batch rows.
4. **A reading needs a consumption.** A provisional quarter-hour without `consumption` is not counted (K1 "with
   measures" rule). A half hour with no such quarter-hour has no row.

## Decision: how two 15-min readings become one 30-min value

**Average of the quarter-hours.**

| Column | Rule |
|---|---|
| MW columns (`consumption`, forecasts, `production_*`, `pumping`) | `round(avg(x))::integer`, NULLs skipped |
| `co2_intensity` | `avg(x)`, NULLs skipped |
| `estimated` | true if any quarter-hour is estimated |
| `stored_at` | latest of the quarter-hours |

A figure missing from every quarter-hour stays NULL, never 0. A figure missing from one quarter-hour uses the other.
A half hour with a single quarter-hour (the newest one, for 15 minutes) is published as it is.

Why average: MW values are powers, and the mean power over a half hour is the mean of its two quarters. Keeping only the
`:00` reading would drop half of the data and make the series jump where provisional turns into consolidated. On real
data the choice is visible: on 2026-09-15 the `tr` consumption was 49260 (12:00), 50223 (12:15), 49492 (12:30) and 49206
(12:45), so the average for 12:00 is 49742 against 49260 for `:00` alone, about 1 %.

## What is NOT verified

What the 30-min value of `eco2mix-national-cons-def` represents (half-hour mean, or the `:00` reading) could not be
checked. On 2026-11-10 `cons-def` ended on 2026-08-31 and `tr` starts on 2026-09-01, so no half hour exists in both.
If cons-def is a half-hour mean, the average is exact. If it is the `:00` value, the provisional rows are off by about 1 %
against what they will become. Acceptable for decision support, and the rule lives only in the second branch of the
view, so changing it touches nothing else.

**Check to run once September is consolidated** (a few `curl` calls on `records`, same half hour in each dataset):

- `eco2mix-national-cons-def`: `date_heure >= date'2026-09-15T12:00:00Z' and date_heure < date'2026-09-15T13:00:00Z' and consommation is not null`
- `eco2mix-national-tr`: same filter

Compare the cons-def value at `12:00` with the mean of the `tr` values at `12:00` and `12:15`, with `12:00` alone and with
`12:15` alone, on three or four half hours. The closest one is the rule.

## Behaviour to know

- **Holes in batch fall back to provisional.** `cons-def` has real holes (the two autumn half hours of every year, and
  `2025-12-31 23:00Z`, see `eco2mix-national-cons-def.md`). If the speed layer holds that period the view shows it as
  `PROVISIONAL`. It only holds data from 2026-09-01, so for old holes there is nothing to show.
- **Scopes never mix.** The batch row of one scope never hides the provisional row of another.
- **`latest` does not use the view** (15-min freshness matters there). Range endpoints (G8) will.
- **No retention yet** on `speed_snapshot`, so provisional rows stay visible until a batch row replaces them.
