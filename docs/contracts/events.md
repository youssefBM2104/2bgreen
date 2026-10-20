# Kafka event contract (K1)

This contract defines the messages that travel on Kafka between the poller, the stream and `platform`. It uses domain language only: ODRÉ field names stay inside the anti-corruption layer of `grid-intelligence`. A change to this file goes through a PR that tags all three of us.

## 1. Who talks to whom

| Topic | Producer | Consumer (group) | Meaning |
|---|---|---|---|
| `grid.snapshot-received` | poller (`grid-intelligence`, track A) | stream (`grid-stream`) | A new provisional reading came from ODRÉ. |
| `grid.snapshot-stored` | stream (`grid-intelligence`, track B) | rule engine in `platform` (`platform-rules`) | The reading is saved in `speed_snapshot`. |

Why two topics: `platform` only reacts to data that is already readable through the read API (K2). If it reacted to the raw topic, it could be told about a reading that is not stored yet.

The batch layer does not use Kafka: it writes `batch_snapshot` directly. So every message on these topics has quality `PROVISIONAL`.

## 2. Topic settings (proposed)

| Setting | Value | Reason |
|---|---|---|
| Partitions | 3 | Enough for ordering per scope and for later regional scopes. Volume is tiny. |
| Retention | 7 days | Allows replay after a bug without growing storage. |
| Key | `scope` as a plain string | Messages of one scope stay in order. |
| Value format | JSON, UTF-8 | Easy to read and to test with fixtures. |

Topics are created by the applications' configuration (not by hand), so a fresh `docker compose up` works.

## 3. Message identity and duplicates

- A message is identified by (`scope`, `observedAt`).
- Kafka delivers **at least once**, so duplicates are normal. Consumers must be idempotent: the stream does an upsert on (`scope`, `observedAt`), and the rule engine ignores a snapshot it has already evaluated.
- Messages of one scope arrive in order. Different scopes have no ordering between them.

## 4. Payload

Same payload on both topics. `grid.snapshot-stored` adds one field, `storedAt`.

| Field | Type | Unit | Notes |
|---|---|---|---|
| `schemaVersion` | integer | | Starts at `1`. Bump on any breaking change. |
| `scope` | string | | `national`, later `region:<code>`. Same as the Kafka key. |
| `observedAt` | string | | UTC, ISO-8601 with `Z`, start of the 15-min period. |
| `co2Intensity` | number | gCO2eq/kWh | Regional values are estimated, see `estimated`. |
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
| `estimated` | boolean | | `true` when CO2 is estimated (regional). `false` for national. |
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

## 8. Consumer groups

| Group | Application | Reads |
|---|---|---|
| `grid-stream` | `grid-intelligence` (stream profile) | `grid.snapshot-received` |
| `platform-rules` | `platform` (rule engine) | `grid.snapshot-stored` |

## 9. Open points

- Invalid messages: log and skip, or send to a dead-letter topic (`grid.snapshot-received.dlt`)?
- Whether a quality upgrade by the batch should also produce an event. Not needed for slice 1.
- Region codes format for `scope` (decided when regional ingestion starts, issue G1).