# Dataset: `eco2mix-national-cons-def`

Source: ODRÉ Opendatasoft Explore API v2.1, RTE éCO2mix, national scope, consolidated then definitive.

## Granularity and update frequency

- Declared on a **15-min grid** but real resolution is **30 min**: the `:15` and `:45` rows exist as placeholders with every measure `null` (confirmed on the latest rows as an alternating pattern — real data on `:00`/`:30`, null on `:15`/`:45`).
- 257,135 of 514,272 total rows carry data; 514,272 / 2 = 257,136, so the 30-min theory holds almost exactly — **at least one real `:00`/`:30` slot is itself missing** (not every half-hour has a value; missing is a valid, expected state, not a bug).
- Update frequency (how often RTE/ODRÉ pushes new consolidated/definitive data) is not yet measured directly — inferred to be monthly from the cons-def/tr handoff below.

## Historical depth

- 514,272 rows, from **2012-01-01 00:00 Paris to 2026-08-31 23:45 Paris** (checked 2026-10-14, **re-confirmed 2026-10-20 — row count unchanged**).
- Cons-def ends 2026-08-31 and `eco2mix-national-tr` starts 2026-09-01 — no overlap. Since the total row count didn't move at all across those 6 days, cons-def clearly doesn't update daily — it updates in discrete monthly jumps (or less often), and September hasn't landed yet as of 2026-10-20.
- Confirms the hypothesis: `tr` holds the current/not-yet-consolidated months; `cons-def` grows by one month once that month is consolidated.

## Timestamp format and timezone

- Primary key is UTC `date_heure`, same convention as tr.
- Caveat: a continuous UTC grid would produce 4 fewer rows in 2026 because of the March DST change. There may be a small duplicate/extra-row quirk around the DST boundary — **check this explicitly when writing the batch upsert logic**, don't assume the row count is DST-clean.

## Consolidated vs definitive

- `nature` **is** a real quality marker here (unlike in tr, where it's constant): two values observed —
  - `"Données consolidées"`: 23,328 rows, covering 2026-01-01 to 2026-08-31
  - `"Données définitives"`: 490,944 rows, covering 2012 to 2025
- **Confirmed 2026-10-20**: the newest `"Données définitives"` row is 2025-12-31 23:45 — all of 2025 and earlier is définitive, all of 2026 (Jan-Aug so far) is still consolidée. The "whole year promoted at once" hypothesis holds. Exact day within the following year when the flip happens is still unverified (we only know it's sometime between Jan 1 and Oct 20 of year+1).
- **Important consequence for the batch layer**: rows already loaded can change `nature` *in place* later (consolidated → definitive). The daily batch resync must **upsert and re-visit previously loaded rows**, not just append new ones at the tail. A cheap detection strategy was proposed but not implemented: one daily aggregate query — count rows grouped by `(year, nature)` — and re-sync a year only when its counts change.

## Fields

Same field set as `eco2mix-national-tr`, with these differences:

| Difference | Detail |
|---|---|
| Missing vs tr | `eolien_terrestre`, `eolien_offshore`, `stockage_batterie`, `destockage_batterie` are **absent** in cons-def. The domain model must treat these as optional and only rely on the fields common to both sources. |
| Type drift vs tr | `ech_comm_allemagne_belgique` and `gaz_cogen` are **`text`** here (they're `int` in tr); `hydraulique_fil_eau_eclusee` is **`int`** here (it's `text` in tr). The ACL must parse every numeric field defensively regardless of the declared type — this is not a one-off, it's at least three fields drifting in both directions. |
| `nature` | real quality marker (see above), vs. a constant placeholder in tr |
| `taux_co2` | present, same meaning as tr (national carbon intensity, gCO2eq/kWh) |

The exchange/sub-breakdown/battery fields identified in `docs/data/eco2mix-national-tr.md` (`ech_physiques`, `ech_comm_*`, `fioul_*`, `gaz_*`, `hydraulique_lacs`/`hydraulique_step_turbinage`, `bioenergies_*`, batteries) are expected to exist here too under the same names, minus the four columns listed as missing above — **not yet confirmed with a live record**, see "Open items."

## Fields explicitly left out of our domain model

Same categories as tr (exchange balance, per-border commercial exchanges, gas/oil/bioenergy sub-breakdowns), minus the battery fields which don't exist in this dataset at all.

## Pagination / exports

- Not yet verified. This is the dataset where it matters most: 514k rows means a full backfill via paged `/records` (typically capped around 100-10,000 rows per page depending on Opendatasoft's limits) would take many calls, whereas `/exports` may return the whole dataset in one call at a different quota cost. **Needs a decision before D3/the batch backfill is implemented.**

## Fixtures

Committed under `grid-intelligence/src/test/resources/odre/`:
- `national-consdef-consolidated-placeholder.json` / `national-consdef-definitive-placeholder.json` — newest row of each `nature`, both null `:45` placeholders. Proof the 30-min-real-grid theory holds at the current boundary, and pins the year-level consolidée/définitive split.
- `national-consdef-consolidated-measured.json` / `national-consdef-definitive-measured.json` — real data-bearing rows for each `nature` (2026-08-31 23:30 consolidée, 2025-12-31 23:30 définitive). Both confirm the known type drift live: `gaz_cogen` and `ech_comm_allemagne_belgique` are strings here (`"172"`/`"1541"` and `"890"`/`"8625"`), `hydraulique_fil_eau_eclusee` is an int (`2225`/`4088`) — exactly matching the tr-vs-cons-def drift already documented.

## Open items for later (need a live curl — the assistant cannot call ODRÉ)

1. Exact day within year+1 when the previous year flips from consolidée to définitive.
2. Whether pagination or `/exports` is the better backfill strategy, and its quota cost.
