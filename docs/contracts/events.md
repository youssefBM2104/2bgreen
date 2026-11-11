# Kafka event contract (K1)

This contract defines the messages that travel on Kafka between the poller, the stream and `platform`. It uses domain language only: ODRÉ field names stay inside the anti-corruption layer of `grid-intelligence`. A change to this file goes through a PR that tags all three of us.

## 1. Who talks to whom

| Topic | Producer | Consumer (group) | Meaning |
|---|---|---|---|
| `grid.snapshot-received` | poller (`grid-intelligence`, track A) | stream (`grid-stream`) | A new provisional reading came from ODRÉ. |
| `grid.snapshot-stored` | stream (`grid-intelligence`, track B) | rule engine in `platform` (`platform-rules`) | The reading is saved in `speed_snapshot`. |
| `grid.band-changed` | stream (`grid-intelligence`, stateful step, G9) | none yet (planned: `BAND_CHANGED` webhooks, U9) | The shared CO2 band (green / amber / red) of a scope changed. Read from `grid.snapshot-stored`. See section 7b. |

Why two topics: `platform` only reacts to data that is already readable through the read API (K2). If it reacted to the raw topic, it could be told about a reading that is not stored yet.

The batch layer does not use Kafka: it writes `batch_snapshot` directly. So every message on these topics has quality `PROVISIONAL`.

## 2. Topic settings (proposed)

| Setting | Value | Reason |
|---|---|---|
| Partitions | 3 | Enough for ordering per scope and for later regional scopes. Volume is tiny. |
| Retention | 7 days | Allows replay after a bug without growing storage. |
| Key | `scope` as a plain string | Messages of one scope stay in order. |
| Value format | JSON, UTF-8 | Easy to read and to test with fixtures. |

The three topics use the same settings. Topics are created by the applications' configuration (not by hand), so a fresh `docker compose up` works. The state store of the band step also has a changelog topic, created by Kafka Streams (`grid-stream-band-state-changelog`), which is internal and not part of this contract.

## 3. Message identity and duplicates

- A message is identified by (`scope`, `observedAt`).
- Kafka delivers **at least once**, so duplicates are normal. Consumers must be idempotent: the stream does an upsert on (`scope`, `observedAt`), and the rule engine ignores a snapshot it has already evaluated.
- Messages of one scope arrive in order. Different scopes have no ordering between them.
- `grid.band-changed` follows the same rule: its identity is (`scope`, `observedAt`) of the snapshot that changed the band, and consumers must be idempotent on it. A duplicate can only follow a crash of the stream (see 7b).

## 4. Payload

Same payload on both topics. `grid.snapshot-stored` adds one field, `storedAt`.

| Field | Type | Unit | Notes |
|---|---|---|---|
| `schemaVersion` | integer | | Starts at `1`. Bump on any breaking change. |
| `scope` | string | | `national`, or `region:<code>` where `<code>` is ODRÉ's `code_insee_region` as is (e.g. `region:32`). Same as the Kafka key. |
| `observedAt` | string | | UTC, ISO-8601 with `Z`, start of the 15-min period. |
| `co2Intensity` | number | gCO2eq/kWh | National: RTE's value. Regional: estimated from the production mix by the regional poll (G7, `docs/data/regional-co2.md`), whole number, imports not counted. See `estimated`. |
| `consumption` | number | MW | |
| `consumptionForecastDay` | number | MW | Forecast of **consumption**, not of CO2. |
| `consumptionForecastDayBefore` | number | MW | Same, issued the day before. |
| `production.nuclear` | number | MW | |
| `production.gas` | number | MW | National only. Null when the source cannot split fossil fuels. |
| `production.coal` | number | MW | Same. |
| `production.oil` | number | MW | Same. |
| `production.thermal` | number | MW | Total of fossil production. Set when the source only gives the aggregate (regional data has no gas/coal/oil split). Optional: may also be set for national. |
| `production.wind` | number | MW | Total (onshore + offshore). |
| `production.solar` | number | MW | |
| `production.hydro` | number | MW | Total. |
| `production.bioenergy` | number | MW | |
| `pumping` | number | MW | Power drawn from the grid to pump water uphill. **Zero or negative** (negative = MW withdrawn, 0 = idle). A positive value is a parsing error. Turbine output is already inside `production.hydro`. |
| `quality` | string | | `PROVISIONAL` on these topics. Later values: `CONSOLIDATED`, `DEFINITIVE` (read API only). |
| `estimated` | boolean | | `true` exactly when `co2Intensity` is an estimate (regional). `false` for national, and for a regional reading with no CO2 (a production source was missing, so no estimate). |
| `storedAt` | string | | **Stored topic only.** UTC time the row was saved. |

Left out on purpose: exchange balance, per-border commercial exchanges, sub-breakdowns of gas, oil and bioenergy, battery fields.

## 5. Missing values

- A value that is not available is **`null` or absent**, never `0`. A zero reading and a missing reading mean different things.
- Consumers must accept both forms.
- **For the ACL (D4):** ODRÉ expresses "missing" in several ways: JSON `null`, numbers delivered as strings, empty strings, and sentinel strings such as `"ND"` or `"-"` (seen on regional consolidated data). All of them become absent/null in the payload. No sentinel and no string number may reach Kafka.
- A snapshot may have most fields missing. It is valid as long as `scope` and `observedAt` are present.
- Rows without measures are **never published**; the ACL drops them. That covers the national forecast tail (future periods with only a forecast) and the regional pending rows (recent periods not yet processed). The test is the same for both: the row has no measured consumption.

## 6. Example

```json
{
  "schemaVersion": 1,
  "scope": "national",
  "observedAt": "2026-10-20T14:45:00Z",
  "co2Intensity": 33,
  "consumption": 49970,
  "consumptionForecastDay": 49950,
  "consumptionForecastDayBefore": 49650,
  "production": {
    "nuclear": 38499,
    "gas": 1200,
    "coal": 0,
    "oil": 0,
    "thermal": null,
    "wind": 6225,
    "solar": 4656,
    "hydro": 4300,
    "bioenergy": 950
  },
  "pumping": -600,
  "quality": "PROVISIONAL",
  "estimated": false
}
```

In the first example, `observedAt`, `consumption`, both forecasts, `nuclear`, `wind` and `solar` come from the committed fixture `national-tr-measured.json`. The other values (`co2Intensity`, `gas`, `coal`, `oil`, `hydro`, `bioenergy`, `pumping`) are illustrative. The second example is entirely illustrative, and shows the case where only the fossil aggregate is known.

Same message with missing values:

```json
{
  "schemaVersion": 1,
  "scope": "national",
  "observedAt": "2026-10-20T15:00:00Z",
  "co2Intensity": 31,
  "consumption": 49500,
  "consumptionForecastDay": null,
  "consumptionForecastDayBefore": 49300,
  "production": {
    "nuclear": 37350,
    "gas": null,
    "coal": null,
    "oil": null,
    "thermal": 1450,
    "wind": 1180,
    "solar": 9100,
    "hydro": null,
    "bioenergy": null
  },
  "pumping": null,
  "quality": "PROVISIONAL",
  "estimated": false
}
```

## 7. Where user thresholds live (decision)

**Decision: `platform` evaluates user thresholds.** The stream knows nothing about thresholds. It stores each snapshot and publishes `grid.snapshot-stored`. The rule engine in `platform` (issue P8) loads each user's rules (alerts, webhooks, target pace), compares them with the snapshot and notifies.

Why:
- Thresholds are per user (a citizen's alert level, a DevOps webhook condition). A stream cannot know them without calling `platform`.
- The volume is tiny (one message per scope every 15 minutes), so checking all rules on every snapshot is cheap.
- Users keep full control over their thresholds. No fixed bands are imposed.

Consequence: the shared kernel `Threshold` is used inside `platform`, not in the stream. A shared band topic (low / medium / high for the traffic light) can be added later without changing this contract.

Rejected: shared bands computed by the stream. It would make the stream more meaningful, but would limit users to fixed bands.

## 7b. Shared band (G9, decision)

**Decision: the stream computes a shared CO2 band per scope and announces its changes.** This adds to section 7 and does not replace it: user thresholds still live in `platform`. The shared band is the same for everybody (the traffic light of the citizen page, F5), so nothing per user is computed in the stream. Section 7 had set this aside ("rejected") to keep the stream thin; G9 reverses that, because the course expects stateful stream processing, and a band that is remembered per scope is a real computation (state, ordering, replay).

**What it does.** For each message of `grid.snapshot-stored`, the stream looks up the last band of the scope in a state store (key = `scope`), computes the band of the new snapshot from `co2Intensity` and publishes on `grid.band-changed` **only when the band changes**.

| Band | CO2 intensity (gCO2eq/kWh) |
|---|---|
| `GREEN` | below 20 |
| `AMBER` | 20 to below 40 |
| `RED` | 40 and above |

A value equal to a limit belongs to the worse band. The limits are the proposal of F5 (`advisory.traffic-light.*` in `platform`), written a second time in `stream.band.*` of `grid-intelligence`, because the two apps share no code. **Keep the two equal.** They are still to be validated as a team (F5).

**Rules.**
- The first snapshot of a scope announces its band (`previousBand` is `null`).
- A snapshot in the same band announces nothing.
- A snapshot whose `observedAt` is not after the last one seen for the scope is ignored: a replay, or a late arrival. It never moves the state back.
- A snapshot without `co2Intensity` has no band: it is ignored and does not count as seen.
- Regional scopes get a band from their estimated CO2 (G7), with the same limits. The estimate is production-based, so a region that produces little and imports a lot (Île-de-France, Bretagne) sits in `RED` most of the time: its band says what it produces, not what its consumers draw. See `docs/data/regional-co2.md`.
- An invalid message is logged and skipped, like on the other topics.

**Key and payload.** Key = `scope`. JSON, UTF-8:

| Field | Type | Notes |
|---|---|---|
| `schemaVersion` | integer | `1`. |
| `scope` | string | Same as the key. |
| `band` | string | `GREEN`, `AMBER` or `RED`: the new band. |
| `previousBand` | string or null | The band before. `null` for the first announcement of a scope. |
| `observedAt` | string | UTC, ISO-8601 with `Z`: the snapshot that changed the band. With `scope`, the identity of the event. |
| `co2Intensity` | number | gCO2eq/kWh of that snapshot. |

```json
{
  "schemaVersion": 1,
  "scope": "national",
  "band": "AMBER",
  "previousBand": "GREEN",
  "observedAt": "2026-10-20T14:45:00Z",
  "co2Intensity": 33
}
```

For the sequence green, green, amber, amber, green the topic gets exactly three events: green (previous `null`), amber (previous green), green (previous amber).

**Why the band step reads `grid.snapshot-stored` and not the previous step of the stream.** A state store belongs to a partition. The stored topic is keyed by scope, so all snapshots of a scope reach the same task whatever key the original message had. It also means a band is only announced for a snapshot that is saved.

**Guarantees.**
- The state store is persistent and has a changelog topic. After a restart, or on another instance, the state is rebuilt from Kafka: no scope is announced again.
- Delivery is **at least once** (the default of Kafka Streams), not exactly once. After a crash between the event being sent and the offsets being committed, a transition can be announced twice. Consumers dedupe on (`scope`, `observedAt`). Exactly-once would need the transactional settings of the broker and is not worth it here.
- `grid.band-changed` is a signal of change, not the current state. To know the colour now, read the latest snapshot (K2): that is what the traffic light of `platform` does, and it also decides when a reading is too old to show a colour, which an event cannot say.

**Consumers.** None yet. The planned one is the `BAND_CHANGED` condition of the DevOps webhooks (U9): `platform` already stores that condition (P7) and the rule engine (P8) skips it until this event exists.

## 8. Consumer groups

| Group | Application | Reads |
|---|---|---|
| `grid-stream` | `grid-intelligence` (stream profile) | `grid.snapshot-received` |
| `platform-rules` | `platform` (rule engine) | `grid.snapshot-stored` |
| `grid-stream` (same application id) | `grid-intelligence` (stream profile, band step) | `grid.snapshot-stored` |
| *(none yet)* | `platform`, for the `BAND_CHANGED` webhooks (U9) | `grid.band-changed` |

## 9. Open points

- The band limits (20 and 40) are written in two places, `advisory.traffic-light.*` (`platform`) and `stream.band.*` (`grid-intelligence`). If one changes, the other must follow. A single source would need the read API to return the band, which changes K2.
- Who consumes `grid.band-changed` (U9), and under which group.

- Invalid messages: log and skip, or send to a dead-letter topic (`grid.snapshot-received.dlt`)?
- Whether a quality upgrade by the batch should also produce an event. Not needed for slice 1.
- ~~Region codes format for `scope`~~ Decided in G1: `region:<code_insee_region>`, the INSEE code kept as ODRÉ sends it (`region:32`, never the name). The regional URLs of the read API (G8) should use the same code.