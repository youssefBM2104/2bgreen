# ADR-002: REST pull and Kafka push between the apps, no cross-schema reads

## Status

Accepted (2026-10-14).

## Context

2026-10-14. `platform` needs grid data in two ways. A screen needs an answer now ("what is the CO2 level?"). And
something must react when a new reading arrives (clean-grid alerts, webhooks), possibly in several places. Both apps
use the same TimescaleDB server, which makes reading the other app's tables tempting.

## Options considered

1. **`platform` reads schema `grid` directly.** No API to write. But the table layout of `grid-intelligence` becomes
   a public contract, and every change of it (merged view, new columns) can break `platform` silently. The boundary
   of Grid Intelligence is gone at the storage level.
2. **REST only.** `platform` would poll the read API to find new readings: wasted calls, a delay, and each new
   consumer would poll again.
3. **Kafka only.** `platform` would keep its own copy of every reading to answer screens. A second store of the same
   data to keep in sync, for no gain.
4. **REST for queries, Kafka for events (chosen).**

## Decision

Option 4.

- **REST pull** when the caller waits for an answer: the read API of `grid-intelligence` (contract K2,
  `docs/api/openapi.yaml`): latest snapshot, and ranges over the merged view, national and regional. In `platform` the
  port `GridReadings` is implemented by the K2 client of module `advisory`; `automation` and `regional` use its public
  API.
- **Kafka push** when something happened and several parts may react: `grid.snapshot-stored` (contract K1,
  `docs/contracts/events.md`), read by the rule engine of `platform`. It is published only after the snapshot is
  stored, so anything `platform` is told about can already be read through K2.
- **No cross-schema reads**, enforced by the database: `grid_app` and `platform_app` have no rights on the other
  schema.

## Consequences

- Good: each app can change its tables freely; only K1 and K2 are contracts. Both are versioned, have example files
  in `docs/`, and contract tests check the code against them.
- Good: a new consumer of readings only adds a Kafka consumer group.
- Bad: when `grid-intelligence` is down, the screens that need grid data answer `503 GRID_UNAVAILABLE` (short
  timeouts, no hanging pages). There is no cache in `platform`.
- Bad: two contracts to keep in step; the JSON body of K2 reuses the K1 codec on purpose so they cannot drift.
