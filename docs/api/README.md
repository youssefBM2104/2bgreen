# Read API contract (K2)

`platform` never reads schema `grid`. It calls this REST API of `grid-intelligence` (port 8081). The contract is `openapi.yaml`. The payload is the K1 event payload ([`../contracts/events.md`](../contracts/events.md), section 4) plus `quality` and `storedAt`. A change to this folder goes through a PR that tags all three of us.

**Internal API.** No authentication. It must stay on the Docker network and must never be exposed through a public route.

## Endpoints

| Endpoint | Status | Meaning |
|---|---|---|
| `GET /api/v1/national/snapshots/latest` | **frozen** (slice 1, built by D8) | Latest national snapshot with measures. A forecast-only row is never returned. |
| `GET /api/v1/national/snapshots?from=&to=` | **frozen** (G8) | Snapshots in a range, 30-min grain, from the merged view. |
| `GET /api/v1/regions/{code}/snapshots/latest` | **frozen** (G8) | Latest snapshot of a region. CO2 is an estimate (G7), `estimated: true`. |
| `GET /api/v1/regions/{code}/snapshots?from=&to=` | **frozen** (G8) | Snapshots of a region in a range. Provisional only until the regional batch (G6). |

The `scope` string (`national`, `region:<code>`) is the Kafka key and the `scope` field of the body, as in K1. It is not the URL syntax: the URL names the resource.

## Rules

- Dates are UTC, ISO-8601 with `Z`. Range: `from` inclusive, `to` exclusive (both apply to the start of each 30-min period), ascending by `observedAt`, empty range is `200` with `items: []`. At most **366 days** between `from` and `to`: a wider request is a `400`, never a cut answer. A period with no data is absent, nothing is filled in.
- The region `{code}` is ODRÉ's INSEE code as is, two digits (`76`). Only the format is checked.
- A missing value is `null` or absent, never `0`. Consumers accept both and ignore unknown fields.
- `quality` is `PROVISIONAL`, `CONSOLIDATED` or `DEFINITIVE`. `latest` is practically always `PROVISIONAL`. A range can mix the three.
- `storedAt` is the time the row was written. It gives the freshness of the data.
- `latest` reads `speed_snapshot` (15 min, freshest). Ranges read the merged view `grid.merged_snapshot` (G4).
- Regional `co2Intensity` is estimated from the production mix (G7, [`../data/regional-co2.md`](../data/regional-co2.md)): direct emissions divided by the region's own production, imports not counted, so it says what the region produces, not what it consumes. `estimated` is `true` exactly when the value is an estimate; a regional row with a production source missing has no CO2 and `estimated: false`.

## Errors

Problem Details (RFC 7807, `application/problem+json`) with a stable `code`. Clients switch on `code`.

| HTTP | `code` | When | What `platform` does |
|---|---|---|---|
| 400 | `INVALID_PARAMETER` | `from` / `to` missing or not a UTC date-time, `from` not before `to`, range wider than 366 days | Bug on the caller side. |
| 404 | `NO_DATA` | Valid resource, no snapshot with measures yet | "Nothing to show". Normal on a fresh start, not an error. |
| 404 | `SCOPE_UNKNOWN` | Region code is not an INSEE code (not two digits). A well-formed code without data is `NO_DATA` (latest) or an empty range. | Bug on the platform side. Log it as an error. |
| 500 | `INTERNAL_ERROR` | Unexpected failure | Grid service down. |
| 503 | `SERVICE_UNAVAILABLE` | Database or service not ready | Grid service down. |

A connection failure is handled like `500` / `503`.

## Examples

Used as fixtures by the frontend, by the stub in `platform` (F4) and by the API tests (D8). `openapi.yaml` points to these files, so they are the single source. Load them from here, do not copy them.

| File | Case |
|---|---|
| `examples/latest-national.json` | Normal data. Values of the fixture `national-tr-measured.json`. |
| `examples/latest-national-missing-values.json` | Some values `null`, some absent, fossil aggregate in `thermal`. |
| `examples/range-national.json` | One row per quality (abridged, rows are not contiguous). |
| `examples/range-region.json` | Two periods of a region, no forecasts. Hydro is missing, so no CO2 estimate (`co2Intensity: null`, `estimated: false`). |
| `examples/regions-latest.json` | Latest of a region. Mix of the fixture `regional-tr-measured.json`, estimated CO2 (`87`, `estimated: true`), no forecasts. |
| `examples/regions-latest-draft.json` | Latest of a region with hydro missing: no CO2 estimate, `estimated: false`. Name kept from before G7, because tests load it. |
| `examples/error-no-data.json` | `404 NO_DATA`. |
| `examples/error-scope-unknown.json` | `404 SCOPE_UNKNOWN` (regional paths). |
| `examples/error-invalid-parameter.json` | `400 INVALID_PARAMETER`. |
| `examples/error-range-too-large.json` | `400 INVALID_PARAMETER`, range wider than 366 days. |
| `examples/error-internal.json` | `500 INTERNAL_ERROR`. |
| `examples/error-unavailable.json` | `503 SERVICE_UNAVAILABLE`. |

## Open points

- **Decided in G8:** 366-day limit (refused, not cut, no pagination: regional trends need months, not years); 30-min grain from the merged view, provisional periods averaged (see `docs/data/merged-view.md`, not yet verified against cons-def); region code = INSEE code, format-only check.
- Still open: a `GET /scopes` endpoint, if a client needs the list of regions.
