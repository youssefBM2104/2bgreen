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
- `date` and `heure` are Paris local time. They are **not** a timestamp, but `heure` is read for one check, see below.

## Daylight saving time (checked 2026-11-10, loaded by G3)

Both clock changes leave a mark, in **every** year (consolidated and definitive alike):

- **Spring: 4 phantom rows.** Paris skips 02:00 to 03:00, yet the data holds rows labelled `02:xx` **and** `03:xx` for the same `date_heure` (01:00Z to 01:45Z). The `02:xx` row is a phantom: it repeats the value of the previous half hour (2025-03-30: `02:30` = 48415 = the `01:30` value, the real `03:30` is 48397; 2026-03-29: same pattern). Rule of the ACL: a row whose `heure` disagrees with its own `date_heure` read in Paris time is dropped. Without it two rows would share one `(scope, observed_at)`.
- **Autumn: 4 missing rows.** On the last Sunday of October the instants `00:00Z` to `00:45Z` do not exist at the source, as in `eco2mix-national-tr`. **Seen directly on 2025-10-26**: the rows go from `23:45Z` (Paris 01:45) straight to `01:00Z` (Paris 02:00), nothing in between. After the placeholders are dropped, 2 half-hours per year are missing (`00:00Z`, `00:30Z`): 28 of the 29 holes of the loaded history. A valid missing state, not a bug.
- The two cancel out in the counts: every definitive UTC year holds exactly 365 x 96 = 35,040 rows (35,136 for a leap year), which is why the row counts alone look clean. 2026 holds 4 more than a continuous grid (23,324 instead of 23,320) because its autumn has not happened yet.
- One more hole: `2025-12-31 23:00Z` (Paris 2026-01-01 00:00), the single real slot missing that the count below already hinted at.
- Result of the first load of the whole history: **257,105 rows** = 257,136 half-hours - 29 holes - 2 phantoms that carried a value in 2026. The merged view (G4) must therefore expect holes in `batch_snapshot` that the provisional table will not fill for the autumn hour: it has no such gap.

## Consolidated vs definitive

- `nature` **is** a real quality marker here (unlike in tr, where it's constant): two values observed —
  - `"Données consolidées"`: 23,328 rows, covering 2026-01-01 to 2026-08-31
  - `"Données définitives"`: 490,944 rows, covering 2012 to 2025
- **Confirmed 2026-10-20**: the newest `"Données définitives"` row is 2025-12-31 23:45 — all of 2025 and earlier is définitive, all of 2026 (Jan-Aug so far) is still consolidée. The "whole year promoted at once" hypothesis holds. Exact day within the following year when the flip happens is still unverified (we only know it's sometime between Jan 1 and Oct 20 of year+1).
- **Important consequence for the batch layer**: rows already loaded can change `nature` *in place* later (consolidated → definitive). The daily batch resync must **upsert and re-visit previously loaded rows**, not just append new ones at the tail. The detection is implemented (G3): one aggregate query counting rows by `(year, nature)`, compared with the counts recorded at the last load, and only the years that differ are downloaded again. See "Change detection" below.

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

## Pagination / exports and quota (decided 2026-11-10, G3)

- `records` is capped at `limit=100`: loading 514k rows through it would cost about 5,000 calls (10 % of the monthly quota). **`exports/jsonl` returns a filtered result in one call**, so the batch uses it for the first load and for every resync.
- The quota is per dataset (50,000 a month, reset on the 1st) and counts **every request, `HEAD` included**: `x-ratelimit-dataset-remaining` went 49997, 49995 across two calls. The daily run therefore avoids any probing call.
- `exports` honours `where` (so `consommation is not null` drops the empty `:15`/`:45` placeholders server-side) and `limit`. It is JSON Lines, one object per line, with no `total_count` / `results` envelope. The order is not guaranteed. `date_heure` comes as `2026-08-31T21:30:00+00:00`.
- The batch asks one UTC year per call: about 17,500 rows, bounded memory, and a failure costs one year, not the whole history.

## Change detection

`records?select=annee,nature,count(*) as n&group_by=year(date_heure) as annee,nature&limit=100` returns the 17 groups in one call. Two traps:

- **The alias is mandatory.** Without `as annee` the year comes back `null`. With `select=year(date_heure) as annee` the value lands under the key `year(date_heure)` and `annee` is `null`. Only the `group_by=year(date_heure) as annee` form fills `annee`, as used by the client.
- **The year is a UTC year, `nature` flips on Paris years.** 2025 holds 4 consolidated rows (Paris 2026-01-01 00:00 to 00:45) next to its definitive ones. The key is therefore the pair `(year, nature)`, and when 2026 turns definitive both 2025 and 2026 change and are reloaded.

## Fixtures

Committed under `grid-intelligence/src/test/resources/odre/`:
- `national-consdef-consolidated-placeholder.json` / `national-consdef-definitive-placeholder.json` — newest row of each `nature`, both null `:45` placeholders. Proof the 30-min-real-grid theory holds at the current boundary, and pins the year-level consolidée/définitive split.
- `national-consdef-consolidated-measured.json` / `national-consdef-definitive-measured.json` — real data-bearing rows for each `nature` (2026-08-31 23:30 consolidée, 2025-12-31 23:30 définitive). Both confirm the known type drift live: `gaz_cogen` and `ech_comm_allemagne_belgique` are strings here (`"172"`/`"1541"` and `"890"`/`"8625"`), `hydraulique_fil_eau_eclusee` is an int (`2225`/`4088`) — exactly matching the tr-vs-cons-def drift already documented.

- `national-consdef-export-sample.jsonl` — 3 real lines of `exports/jsonl` (newest measured rows), the shape of the export.
- `national-consdef-dst-spring-2026-trimmed.jsonl` / `national-consdef-dst-spring-2025-definitive-trimmed.jsonl` — the spring DST window as the API returns it, **cut down** to `date_heure`, `heure`, `consommation`, with `nature` added from the group counts (all rows of those periods have it). Proof of the phantom rows in a consolidated and in a definitive year.
- `national-consdef-counts.json` — the 17 `(year, nature)` groups as measured on 2026-11-10, in the shape of the `records` answer.

## Open items for later (need a live curl — the assistant cannot call ODRÉ)

1. Exact day within year+1 when the previous year flips from consolidée to définitive. The daily run will show it in the logs (a year reloaded with a changed `nature`).
2. Whether the 2026 autumn hole (2026-10-25) is also absent from the data once 2026 reaches cons-def: expected, same mechanism.
