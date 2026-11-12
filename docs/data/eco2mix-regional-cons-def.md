# Dataset: `eco2mix-regional-cons-def`

Source: ODRÉ Opendatasoft Explore API v2.1, RTE éCO2mix, regional scope, consolidated then definitive.

One row per `(region, date_heure)` pair, same multiplexing as regional-tr. `total_count` = 2,867,272 (where `consommation` is not null, as of 2026-10-20) — roughly 13x the size of national cons-def's measured-row count, consistent with ~13 regions over a comparable or longer history.

## Granularity and update frequency

- **30-min real resolution, but structurally different from national cons-def.** National cons-def is stored on a 15-min grid padded with null `:15`/`:45` placeholder rows (half the dataset is null by construction). Régional cons-def is **not** padded that way: only 24 null rows exist in the entire 2,867,296-row dataset (checked via `where=consommation is null`) — negligible, not a systematic pattern. So régional cons-def doesn't carry the quarter-hour placeholder structure at all; treat it as a genuine 30-min (or near-30-min, with rare sparse gaps) dataset.
- Update frequency not measured directly; data.gouv.fr's "refreshed daily" claim is plausible but unconfirmed.

## Historical depth

- **Checked 2026-10-20: oldest row is 2012-12-31T23:00:00+00:00 UTC = 2013-01-01 00:00 Paris.** This **contradicts** data.gouv.fr's claim of "from January 2021" by 8 years — correcting that here. History goes back to 2013, one year after national's 2012 start.

## Timestamp format and timezone

- Same convention: `date_heure` UTC is authoritative, `date`/`heure` are Paris local — ignore the latter two.

## Consolidated vs definitive

- **Checked 2026-10-20**: the newest `"Données définitives"` row is 2025-12-31 23:30 — all of 2025 is already définitive for régional too, same as national. This is **consistent with** (not contradicting) data.gouv.fr's "Q2 of year+1" claim, since Q2 2026 has already passed by October. No discrepancy found versus national — but the exact day of the flip is still unpinned, so a *difference* in timing between national and régional (e.g. régional flipping later, in Q2, while national flips earlier) remains possible and unverified.

## Region identification

Same as regional-tr: `code_insee_region` + `libelle_region`.

## Carbon intensity

- **Confirmed by direct sample: no `taux_co2` field here either.** Same conclusion as regional-tr — regional CO2 must be estimated (issue G7), consolidated/définitive data included.
- No `prevision_j`/`prevision_j1` fields either (consistent with regional-tr, and expected since cons-def never carries forecasts even on the national side).

## Fields (from one live sample, Occitanie, 2026-08-31 consolidée)

| Field | Type (observed) | Unit | Notes |
|---|---|---|---|
| `code_insee_region` | string | — | |
| `libelle_region` | string | — | |
| `nature` | string | — | real quality marker here (unlike tr): `"Données consolidées"` observed, `"Données définitives"` expected for older years, not yet captured |
| `date_heure` | datetime | UTC | |
| `date`, `heure` | date / time | Europe/Paris | ignore |
| `consommation` | int | MW | |
| `thermique` | int | MW | aggregate, same as regional-tr |
| `nucleaire`, `solaire`, `hydraulique`, `bioenergies` | int | MW | |
| `eolien` | **string** in this sample (`"376"`) | MW | **type drift confirmed**: int in the regional-tr sample, string here — same defensive-parsing discipline needed as national |
| `pompage`, `stockage_batterie`, `destockage_batterie` | int in this sample | MW | int here, string in the regional-tr sample — type drift goes both directions, same as national's `hydraulique_fil_eau_eclusee` |
| `ech_physiques` | int | MW | |
| `eolien_terrestre`, `eolien_offshore` | int | MW | sub-breakdown of `eolien` — present here, **absent in the regional-tr sample** (reverse of the national tr/cons-def pattern) |
| `tco_<source>`, `tch_<source>` | float | % | same 6 source pairs as regional-tr |
| `column_30` | always `null` | — | same Opendatasoft export artifact as regional-tr's `column_68` — number varies, treat as noise |

## Missing-value sentinels — relevant to K1

Beyond JSON `null`, at least one field (`eolien`) has been observed carrying a **text sentinel instead of null**, on rows where every other measure is `null`:
- `"ND"` on the oldest row in the dataset (Bretagne, 2013-01-01) — plausibly "non disponible" / wind not yet tracked for that region at that date.
- `"-"` on one of the 24 sparse-null rows (Normandie, 2026-01-01).

This means the ACL/deserializer cannot treat "missing" as simply `null`-or-absent: it must also recognize these string sentinels and map them to the same missing-value representation. K1's still-open "missing value conventions" decision should account for this — the source itself isn't consistent about how it marks "no data," so the ACL has to normalize at least three shapes (`null`, a numeric-as-string, and a sentinel-as-string) into one.

## Fields explicitly left out of our domain model

Same as regional-tr: `tco_*`/`tch_*` and the trailing `column_N` artifact.

## Fixtures

Committed under `grid-intelligence/src/test/resources/odre/`:
- `regional-consdef-measured.json` (Occitanie, 2026-08-31, consolidée, non-null)
- `regional-consdef-definitive.json` (Pays de la Loire, 2025-12-31, définitive, non-null — also shows `eolien` as a string `"870"` here, and the `eolien_terrestre`/`eolien_offshore` split present, consistent with the earlier cons-def sample)
- `regional-consdef-oldest-row-ND-sentinel.json` (Bretagne, 2013-01-01, the dataset's oldest row — `eolien: "ND"`)
- `regional-consdef-sparse-null-dash-sentinel.json` (Normandie, 2026-01-01 — `eolien: "-"`, one of only 24 null rows in the whole dataset)
- `regional-consdef-counts.json` (captured 2026-11-11: rows per UTC year and `nature`, the change-detection query of G6)
- `regional-consdef-export-dst-2025.jsonl` (captured 2026-11-11: export with the G6 `select`, Occitanie and PACA, 2025-03-30 00:00–02:00 UTC, with the DST phantoms)

## Checked on 2026-11-11 (issue G6)

- **12 regions**, the same codes as régional-tr, **99,310 rows each since 2021-01-01 UTC**: a clean 30-min grid.
- **Same spring DST phantoms as national cons-def.** On 2025-03-30, `01:00Z` comes twice, labelled `02:00` and `03:00` (same values), and `01:30Z` twice, labelled `02:30` (a copy of `01:30`) and `03:30`. The row whose `heure` disagrees with `date_heure` in Paris time is the phantom; the batch drops it with the national rule.
- **Export throughput is the bottleneck**: a regional year with the G6 `select` is 57 MB / 210,240 rows; the first byte came after 2 s, the whole file after 18.6 min (about 50 kB/s).

## How the batch reads this dataset (G6)

- **Use case** `SyncRegionalConsolidated`, same algorithm as the national batch (G3, shared in `ConsolidatedYears`): one count call per run (`year(date_heure)`, `nature`, all regions together), and a whole-UTC-year export only for the years whose counts changed. Years before `batch.regional.first-year` (2021) are ignored. Its sync state is kept under the key `regional` in `batch_sync_state`, apart from the national one.
- **ACL** `RegionalConsolidatedMapping`: scope `region:<code>`, `thermique` to `production.thermal`, quality from `nature`, DST phantoms dropped, rows without consumption or region code dropped. The export asks only for the snapshot columns (`select`).
- **CO2**: the use case applies the G7 estimate (`RegionalCo2Estimator`) to every reading, so `batch_snapshot` holds `estimated = true` regional CO2, like `speed_snapshot`.
- **Upsert**: the G3 rules (definitive never replaced by consolidated, nothing written when nothing differs).
- **Not loaded**: `tco_*` / `tch_*`, exchanges, batteries, onshore/offshore split. No feature needs them, and the coverage rate of U11 is computed from consumption and production.

## Open items for later (need a live curl)

1. ~~Distinct region count and the actual list of codes.~~ Done for 2021+ (G6): the 12 codes of régional-tr.
2. Exact day of the régional définitive flip, to compare against national's timing.
3. Whether `"ND"`/`"-"` (or other sentinels) appear on fields besides `eolien`, and whether they appear in régional-tr too.
