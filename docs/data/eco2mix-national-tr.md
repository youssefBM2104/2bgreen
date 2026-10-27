# Dataset: `eco2mix-national-tr`

Source: ODRÉ Opendatasoft Explore API v2.1, RTE éCO2mix, national scope, real-time/provisional.

## Granularity and update frequency

- Time step is **15 min**.
- Data lags real time by roughly 15-30 min (observed at 11:23 Paris: newest row with measures had `date_heure` = 09:00 UTC, i.e. ~2h15 behind wall clock but only ~15-30 min behind what RTE had actually measured at that point — the lag is in RTE's own publication, not in ODRÉ).
- The dataset also contains **future, forecast-only rows** (up to the end of tomorrow), and they come in **two distinct shapes** depending on how far out they are — refines the earlier "every measure is null" note:
  - **Rest of today** (e.g. checked 2026-10-20 15:00 UTC): `consommation` and every production field are `null`, but `prevision_j`, `prevision_j1`, **and** the cross-border exchange fields (`ech_comm_*`) are all populated.
  - **Tomorrow** (verified 2026-10-20 with a row for 2026-10-21 00:00 Paris): `prevision_j1` is the **only** non-null field. `prevision_j` and every `ech_comm_*` field are `null` too. Makes sense: `prevision_j` is today's own, intraday-updated forecast and simply has no value for a day it doesn't cover; `prevision_j1` was produced the day before and already covers tomorrow. The exchange forecasts (`ech_comm_*`) follow `prevision_j`'s same-day-only pattern, not `prevision_j1`'s.
  - Either way: **"latest" must mean the latest row with non-null `consommation` / `taux_co2`**, not the latest row by `date_heure`, and the ACL must not turn either shape of forecast-only row into a `GridSnapshot`.
  - Verified 2026-10-20: 4896 total rows, 4772 with `consommation` non-null, 124 forecast-only — split cleanly as 96 "tomorrow" rows (a full day, confirmed by a direct query) + 28 "rest of today" rows (7 hours from the 15:00 UTC checkpoint to Paris midnight) = 124.

## Historical depth

- **Retention hypothesis from CLAUDE.md is contradicted by the data — correcting it here.** Checked twice:
  - 2026-10-14: 4320 rows, oldest = 2026-09-01 00:00 Paris (45 days of history + forecast tail).
  - 2026-10-20 (6 days later): 4896 rows, oldest = **still** 2026-09-01 00:00 Paris (51 days). The +576 rows is exactly 6 days × 96 rows/day.
  - Conclusion: the dataset is **growing from a fixed start point (2026-09-01)**, not sliding a rolling window. September has not been erased despite mid-October having passed. The "month M erased in mid-M+1" rule, which CLAUDE.md borrowed from ODRÉ's description of *regional*-tr, does **not** hold for national-tr — at least not by day 20 of the following month.
  - Still open: whether erasure happens later (e.g. only once `cons-def` has consolidated that month), or whether national-tr simply doesn't roll off the way regional-tr is documented to. Worth one more check in a few weeks (e.g. early November) to see if September finally drops, or if the dataset just keeps growing indefinitely.

## Timestamp format and timezone

- `date_heure` is **UTC** and is the **only** timestamp field to use.
- `date` and `heure` are Paris local time (CET/CEST) — **ignore them**, they carry a DST ambiguity that `date_heure` doesn't have.
- **Autumn DST night: one hour is missing at the source** (verified 2026-10-27, D6 live run). On 2026-10-25, when Paris went from 03:00 CEST back to 02:00 CET, the dataset jumps from `2026-10-24T23:45Z` (`heure` 01:45) to `2026-10-25T01:00Z` (`heure` 02:00): the 4 rows `00:00Z`–`00:45Z`, i.e. the *first* occurrence of the repeated 02:00–02:45, do not exist. RTE apparently keyed that night by local time and kept one copy of the repeated hour. Not a bug of our pipeline: `speed_snapshot` has the same hole, which is a valid "missing" state (K1). Same 4-row shortfall as the DST caveat noted for cons-def; whether the batch layer can fill it from cons-def is to check later.

## Consolidated vs definitive

- Not applicable to this dataset. `nature` has a single constant value ("Données temps réel") across all rows — it is **not** a quality marker here. Quality for tr rows is derived by our own pipeline (always `PROVISIONAL`), not read from the source.

## Fields

| Field | Type (declared by API) | Unit | Notes |
|---|---|---|---|
| `date_heure` | datetime | UTC | the only timestamp to use |
| `date`, `heure` | date / time | Europe/Paris | ignore (DST) |
| `consommation` | int | MW | null on forecast-only rows |
| `prevision_j`, `prevision_j1` | int | MW | **consumption** forecasts (day, day-before), not CO2 forecasts — do not confuse with carbon intensity |
| `taux_co2` | int | gCO2eq/kWh | national carbon intensity — present here, absent in regional (see G7) |
| `nucleaire`, `gaz`, `charbon`, `fioul`, `eolien`, `solaire`, `hydraulique`, `bioenergies`, `pompage` | int | MW | production by source; `pompage` is pumping (negative = pumping, consult sign convention when writing the ACL) |
| `eolien_terrestre`, `eolien_offshore`, `stockage_batterie`, `destockage_batterie` | int | MW | present in tr, **absent in cons-def** — domain model must treat as optional (common-core fields only are guaranteed across both sources) |
| `hydraulique_fil_eau_eclusee` | **text** (e.g. `"2238"`) | MW | typed as text here but typed `int` in cons-def — parse defensively regardless of declared type |
| `ech_comm_allemagne_belgique`, `gaz_cogen` | int | MW | typed `int` here but **`text` in cons-def** — same defensive-parsing note |
| `ech_physiques` | int | MW | net physical cross-border exchange (total); null on forecast-only rows |
| `ech_comm_angleterre`, `ech_comm_espagne`, `ech_comm_italie`, `ech_comm_suisse`, `ech_comm_allemagne_belgique` | int | MW | per-border commercial exchange; **stay populated even on forecast-only rows** (forecasted independently of production) |
| `fioul_tac`, `fioul_cogen`, `fioul_autres` | int | MW | sub-breakdown of `fioul` |
| `gaz_tac`, `gaz_cogen`, `gaz_ccg`, `gaz_autres` | int | MW | sub-breakdown of `gaz` |
| `hydraulique_lacs`, `hydraulique_step_turbinage` | int | MW | sub-breakdown of `hydraulique` (alongside `hydraulique_fil_eau_eclusee`) |
| `bioenergies_dechets`, `bioenergies_biomasse`, `bioenergies_biogaz` | int | MW | sub-breakdown of `bioenergies` |

All measures are nullable. Every numeric field must be parsed defensively (declared type is not trustworthy and drifts between tr and cons-def for at least three known fields).

## Fields explicitly left out of our domain model

Per the K1 snapshot shape (CLAUDE.md "Snapshot fields"), confirmed against the real field names above:
- exchange balance: `ech_physiques`
- per-border commercial exchanges: `ech_comm_angleterre`, `ech_comm_espagne`, `ech_comm_italie`, `ech_comm_suisse`, `ech_comm_allemagne_belgique`
- sub-breakdowns of gas/oil/bioenergy: `fioul_tac`/`fioul_cogen`/`fioul_autres`, `gaz_tac`/`gaz_cogen`/`gaz_ccg`/`gaz_autres`, `bioenergies_dechets`/`bioenergies_biomasse`/`bioenergies_biogaz` (and `hydraulique_lacs`/`hydraulique_step_turbinage`, a hydro sub-breakdown not explicitly named in CLAUDE.md but same category)
- battery fields: `stockage_batterie`, `destockage_batterie`

These exist in the raw dataset but are not carried into `GridSnapshot`.

## Pagination / exports

- Not yet verified for this dataset specifically — still open whether `/exports` is preferable to paged `/records` for a full backfill, and how each counts against the 50,000 calls/user/month quota. This matters more for `cons-def` (large history) than for `tr` (45-day window), but worth confirming here too since the poller (D3) will hit this dataset repeatedly.

## Fixtures

Required by the hard rule "CI and tests never call the live ODRÉ API". Committed under `grid-intelligence/src/test/resources/odre/`, captured live on 2026-10-20:
- `national-tr-measured.json` — latest row with non-null `consommation` (`date_heure` 2026-10-20T14:45:00+00:00)
- `national-tr-forecast-only.json` — "rest of today" forecast row (`date_heure` 2026-10-20T15:00:00+00:00): `prevision_j`, `prevision_j1` and `ech_comm_*` all populated, everything else null
- `national-tr-forecast-tomorrow.json` — genuine next-day forecast row (`date_heure` 2026-10-20T22:00:00+00:00, i.e. 2026-10-21 00:00 Paris): only `prevision_j1` is non-null — `prevision_j` and `ech_comm_*` are null here too, unlike the "rest of today" shape above

All three are raw, unmodified Explore v2.1 API responses (the `{"total_count": ..., "results": [...]}` envelope), so the ACL/deserializer tests exercise the real response shape and both forecast-only shapes.

One more fixture is **hand-made, not a live capture** (D4), because no national-tr row with missing values has been observed:
- `national-tr-missing-values-synthetic.json` — copy of `national-tr-measured.json` where some measures were replaced by every "missing" shape the ACL must handle: `taux_co2` null, `prevision_j` `" "`, `gaz` `""`, `eolien` `"ND"` and `solaire` `"-"` (the sentinels seen in regional data), `bioenergies` `"abc"`, plus `hydraulique` `"3768"` (a number sent as text, which must be kept).

## Open items for later

- Whether `/exports` beats paged `/records` for backfill, and how each counts against quota — still unverified, matters more for `cons-def`.
- Re-check the retention question in early November: does September ever get erased, or does the dataset just keep growing from 2026-09-01?
