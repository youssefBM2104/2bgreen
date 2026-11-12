# ADR-007: User thresholds are evaluated in `platform`, the shared CO2 band in the stream

## Status

Accepted (2026-11-12). Records two decisions of the Kafka contract (K1, `docs/contracts/events.md`, sections 7 and 7b),
the second one refining the first.

## Context

Two kinds of "level" exist in 2bGreen. Each user has their own thresholds (a citizen's alert at 50 gCO2eq/kWh, a DevOps
webhook condition, a regional target pace). And everybody shares one traffic light (green, amber, red). Some part of
the system must compare readings with these levels as they arrive, and it must be clear where each comparison
lives. The stream (`grid-intelligence`) knows nothing about users; `platform` knows nothing about ODRÉ (ADR-001).

## Options considered

1. **The stream evaluates user thresholds.** It would have to know every user's rules, so it would call `platform` or
   own copies of user data. That breaks the boundary of ADR-001.
2. **`platform` evaluates everything, including the shared band.** Simple, but the stream stays a copy-and-store step
   with nothing to remember, and every consumer that wants "the band changed" must recompute it.
3. **Per-user thresholds in `platform`, shared band in the stream (chosen).**

## Decision

Option 3.

- **User thresholds**: the rule engine of `platform` (group `platform-rules`) reads `grid.snapshot-stored` and compares
  each user's rules with the snapshot. The volume is one message per scope every 15 minutes, so checking all rules on
  each message is cheap. A shared kernel `Threshold` was planned in K1 but never written: each rule carries its own threshold value.
- **Shared band**: a stateful step of the stream keeps the last band per scope in a state store and publishes
  `grid.band-changed` only when the band changes (limits 20 and 40 gCO2eq/kWh, equal belongs to the worse band; replayed or
  older readings are ignored). It is real stream processing: state, order per scope, replay.
- The limits are written twice, in `stream.band.*` and in `advisory.traffic-light.*`, and must be kept equal.

## Consequences

- Good: the stream never needs user data, and users keep full control of their own levels.
- Good: `grid.band-changed` is a ready-made event for several consumers (planned: `BAND_CHANGED` webhooks, U9).
- Bad: the band limits are duplicated between two apps, with no shared code (ADR-001). A test per app guards its own
  copy, nothing checks that the two agree.
- Bad: nothing consumes `grid.band-changed` yet, and delivery is at least once: a duplicate is possible after a crash,
  so consumers must dedupe on (`scope`, `observedAt`).
- Open: the 20 and 40 limits are a proposal from autumn data, to validate as a team.
