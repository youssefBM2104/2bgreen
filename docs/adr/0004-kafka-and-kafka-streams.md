# ADR-004: Kafka as the event log, Kafka Streams for processing

## Status

Accepted (2026-10-14). Updated 2026-11-12 to match what was built since (the decision itself is unchanged).

## Context

2026-10-14. The speed path moves readings from the poller to storage, then tells `platform` that a reading is stored.
The volume is tiny: one message per scope (France and 12 regions) every 15 minutes. What matters is:

- **order per scope**: two readings of one region must be processed in order;
- **decoupling**: the poller must keep working when the stream or `platform` is down, and catch up afterwards;
- **several consumers** of one event (rule engine now, webhooks and others later);
- **replay** after a bug.

## Options considered

1. **No broker**: the poller writes to the database and calls `platform`. Nothing is buffered when a part is down,
   and every new consumer is a new call in the poller.
2. **RabbitMQ.** Fine for queues, but a message is gone once consumed: no replay, and keeping order per scope with
   several consumers needs extra work.
3. **Kafka (chosen)**, with **Kafka Streams** as a library inside `grid-intelligence`, rather than a plain consumer
   or a processing cluster (Flink, Spark), which would be one more thing to run.

## Decision

Kafka in KRaft mode (no ZooKeeper), one broker in Compose. Topics `grid.snapshot-received` (poller → stream),
`grid.snapshot-stored` (stream → rule engine of `platform`) and `grid.band-changed` (stream). Key = `scope`, so all
readings of a scope go to the same partition and stay in order. 3 partitions, 7 days retention, JSON values. The
contract is `docs/contracts/events.md`.

- **At least once**, not exactly once. Writes are idempotent upserts on (`scope`, `observed_at`) and the stream
  stores before it publishes, so a replayed message changes nothing.
- **Kafka Streams** does stateful work: a state store per scope (backed by a changelog topic) remembers
  the last CO2 band and emits `grid.band-changed` only when it changes, ignoring replays and late readings.

## Consequences

- Good: order per scope, decoupling and replay come from Kafka itself; adding a consumer is a new group, the
  producer does not change.
- Good: Kafka Streams keeps state fault-tolerant without a cluster to run.
- Bad: Kafka is heavy for this volume. It is justified by ordering, decoupling, several consumers and replay, not by
  load. A broker to run in dev and CI (Testcontainers).
- Bad: duplicates are normal; every consumer must be idempotent.
- Open: an invalid message is logged and skipped; a dead-letter topic is still an option.
