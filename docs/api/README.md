# Read API contract (K2)

`platform` never reads schema `grid`. It calls this REST API of `grid-intelligence` (port 8081). The contract is `openapi.yaml`. The payload is the K1 event payload ([`../contracts/events.md`](../contracts/events.md), section 4) plus `quality` and `storedAt`. A change to this folder goes through a PR that tags all three of us.

**Internal API.** No authentication. It must stay on the Docker network and must never be exposed through a public route.

## Endpoints

| Endpoint | Status | Meaning |
|---|---|---|
| `GET /api/v1/national/snapshots/latest` | **frozen** (slice 1, built by D8) | Latest national snapshot with measures. A forecast-only row is never returned. |
| `GET /api/v1/national/snapshots?from=&to=` | **draft** | Snapshots in a range. Not frozen until G8. |
| `GET /api/v1/regions/{code}/snapshots/latest` and `.../snapshots?from=&to=` | **reserved** | Path shape only. The region code format is decided in G1. |

The `scope` string (`national`, `region:<code>`) is the Kafka key and the `scope` field of the body, as in K1. It is not the URL syntax: the URL names the resource.

## Rules

- Dates are UTC, ISO-8601 with `Z`. Range: `from` inclusive, `to` exclusive, ascending by `observedAt`, empty range is `200` with `items: []`.
- A missing value is `null` or absent, never `0`. Consumers accept both and ignore unknown fields.
- `quality` is `PROVISIONAL`, `CONSOLIDATED` or `DEFINITIVE`. `latest` is practically always `PROVISIONAL`. A range can mix the three once the merged view exists.
- `storedAt` is the time the row was written. It gives the freshness of the data.
- `latest` reads `speed_snapshot` today and the merged view later, without a contract change.

## Errors

Problem Details (RFC 7807, `application/problem+json`) with a stable `code`. Clients switch on `code`.

| HTTP | `code` | When | What `platform` does |
|---|---|---|---|
| 400 | `INVALID_PARAMETER` | Bad date format, `from` not before `to` (range, draft) | Bug on the caller side. |
| 404 | `NO_DATA` | Valid resource, no snapshot with measures yet | "Nothing to show". Normal on a fresh start, not an error. |
| 404 | `SCOPE_UNKNOWN` | Region code not recognised (reserved regional paths) | Bug on the platform side. Log it as an error. |
| 500 | `INTERNAL_ERROR` | Unexpected failure | Grid service down. |
| 503 | `SERVICE_UNAVAILABLE` | Database or service not ready | Grid service down. |

A connection failure is handled like `500` / `503`.

## Examples

Used as fixtures by the frontend, by the stub in `platform` (F4) and by the API tests (D8). `openapi.yaml` points to these files, so they are the single source. Load them from here, do not copy them.

| File | Case |
|---|---|
| `examples/latest-national.json` | Normal data. Values of the fixture `national-tr-measured.json`. |
| `examples/latest-national-missing-values.json` | Some values `null`, some absent, fossil aggregate in `thermal`. |
| `examples/range-national.json` | Draft. One row per quality (abridged, rows are not contiguous). |
| `examples/regions-latest-draft.json` | Reserved regional path. No CO2, no forecasts, `estimated: true`. |
| `examples/error-no-data.json` | `404 NO_DATA`. |
| `examples/error-scope-unknown.json` | `404 SCOPE_UNKNOWN` (reserved regional path). |
| `examples/error-invalid-parameter.json` | `400 INVALID_PARAMETER`. |
| `examples/error-internal.json` | `500 INTERNAL_ERROR`. |
| `examples/error-unavailable.json` | `503 SERVICE_UNAVAILABLE`. |

## Open points

- Maximum range and pagination of the range endpoints. Regional trends (U12, U14) need months of history, so no cap is fixed.
- Grain of range results: 15 min provisional, 30 min once the merged view exists (G4).
- Region code format (G1). A `GET /scopes` endpoint may be needed later.
