# Dataset: `eco2mix-regional-tr`

Source: ODRÉ Opendatasoft Explore API v2.1, RTE éCO2mix, regional scope, real-time/provisional.

One row per `(region, date_heure)` pair — this dataset multiplexes all regions, unlike the national datasets which have one row per timestamp. `total_count` (57,264 rows with non-null `consommation` as of 2026-10-20) spans every region, not one.

## Granularity and update frequency

- 15-min step, confirmed by the `total_count` arithmetic below.
- Unlike national-tr, this dataset does **not** carry a multi-day forecast tail: the newest row observed (2026-10-20 23:45 Paris, Occitanie) is null, and it's the *last slot of today*, not a row reaching into tomorrow. Makes sense — regional has no `prevision_j1` field to populate a forecast row with, so there's nothing to forecast. The trailing nulls are a few hours of processing lag at the end of the current day, not a forecast window.

## Historical depth

- **Checked 2026-10-20**: oldest row is **2026-09-01 00:00 Paris** — identical start date to national-tr. Same conclusion as national: the "erased mid-M+1" hypothesis is **false** here too. September hasn't been erased despite mid-October having passed.
- **Region count confirmed by arithmetic, not a direct distinct-value query**: unfiltered `total_count` = 57,600. From 2026-09-01 00:00 Paris to 2026-10-20 23:45 Paris is 50 days × 96 slots/day = 4,800 rows per region. `57,600 / 4,800 = 12` exactly — so this dataset covers **12 regions** (metropolitan France, presumably excluding Corse or counting it differently — not confirmed by name, just by count).

## Timestamp format and timezone

- Same convention as national: `date_heure` is UTC, `date`/`heure` are Paris local time — ignore the latter two, use `date_heure` only.

## Region identification

- `code_insee_region` (string, e.g. `"32"`) and `libelle_region` (e.g. `"Hauts-de-France"`) identify the region. Per the project's privacy stance (CLAUDE.md, feature U3), only the region **code** should ever be stored for a citizen's region preference — never derived geolocation.
- 12 distinct regions, per the arithmetic above (not a direct distinct-value query — would still be worth running once to get the actual list of codes/names, since arithmetic only gives the count).

## Carbon intensity — priority question for D1

- **Confirmed by direct sample: no `taux_co2` field.** Regional data carries no carbon-intensity measure at all. CO2 for regions must be **estimated** from production mix using ADEME emission factors — this is issue G7, not solved here. This file just confirms the absence that makes G7 necessary.
- Also confirmed: **no `prevision_j` / `prevision_j1` fields** — regional has no consumption forecasts either, unlike national.

## Fields (from one live sample, Hauts-de-France, 2026-10-20)

| Field | Type (observed) | Unit | Notes |
|---|---|---|---|
| `code_insee_region` | string | — | region code |
| `libelle_region` | string | — | region name, human-readable |
| `nature` | string | — | constant `"Données temps réel"` in this dataset, same non-marker behavior as national-tr |
| `date_heure` | datetime | UTC | the only timestamp to use |
| `date`, `heure` | date / time | Europe/Paris | ignore (DST) |
| `consommation` | int | MW | |
| `thermique` | int | MW | **aggregate** fossil production — regional does not split `fioul`/`charbon`/`gaz` like national does |
| `nucleaire`, `eolien`, `solaire`, `hydraulique`, `bioenergies` | int | MW | production by source |
| `pompage`, `stockage_batterie`, `destockage_batterie` | **string** in this sample (e.g. `"0"`) | MW | **observed as text here but int in the regional cons-def sample** — same defensive-parsing discipline as national, type drift confirmed in regional too |
| `ech_physiques` | int | MW | net physical exchange, same meaning as national |
| `tco_<source>`, `tch_<source>` | float | % | coverage rate / load rate per source, for `thermique`, `nucleaire`, `eolien`, `solaire`, `hydraulique`, `bioenergies` (6 pairs = 12 fields). Confirms the TCO/TCH fields CLAUDE.md flagged as unverified. Not part of `GridSnapshot` — operational detail, not needed for the citizen/DevOps/regional features as currently scoped. |
| `column_68` | always `null` | — | looks like an Opendatasoft export artifact, not a real field. The regional cons-def sample has an equivalent `column_30` — the number appears to vary, so don't treat the name as stable; treat any trailing `column_N` as noise to discard in the ACL. |

**Not present in this sample** (observed absent, single-sample caveat applies): `eolien_terrestre`, `eolien_offshore` — present in regional **cons-def** instead (see that file). This is the reverse of the national pattern, where tr has the eolien split and cons-def doesn't.

## Fields explicitly left out of our domain model

- `tco_*` / `tch_*` (coverage/load rates) — operational RTE metrics, not part of the K1 snapshot shape.
- the trailing `column_N` artifact field.

## Fixtures

Committed under `grid-intelligence/src/test/resources/odre/`:
- `regional-tr-measured.json` (Hauts-de-France, 2026-10-20, non-null — captured live)
- `regional-tr-pending.json` (Occitanie, 2026-10-20 23:45, all-null — the "not yet measured" tail at the end of today, confirming there's no multi-day forecast tail here unlike national-tr)

## Open items for later (need a live curl)

1. Get the actual list of the 12 region codes/names (currently only the count is confirmed, by arithmetic).
2. Confirm the `eolien_terrestre`/`eolien_offshore` presence/absence pattern across more than one sample (observed absent here, present in regional cons-def — opposite of the national tr/cons-def pattern, but based on single samples each).
3. Whether the sentinel strings `"ND"` / `"-"` observed in regional cons-def's `eolien` field (see that file) also occur here.
