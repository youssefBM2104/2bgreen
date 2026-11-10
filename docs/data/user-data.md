# User data inventory (W5)

What `platform` stores about its users, per persona: why, which module owns it, how long we keep it, and whether it is personal data. This is the input of P7 (schema of `platform`): every table of P7 must come from a row below, and every row below must end up in a table.

Built from the U issues (#33 to #48) and from P7 (#31) and P8 (#32) as they stand on GitHub. **Status: draft for team review.** Fields come from the issues. Retention periods and the personal-data calls are proposals (marked *proposal*): the issues do not set them, nobody has validated them yet. The review is an acceptance criterion of issue #22.

## Scope and conventions

- Only data **created by the users** is listed. Grid data (ODRÉ, `speed_snapshot`, `batch_snapshot`) is public and lives in `grid-intelligence`: see `docs/data/eco2mix-*.md`.
- Everything below lives in schema `platform`, written by the role `platform_app`. `grid-intelligence` never sees it (it knows no user).
- **Owner module** = the Spring Modulith module that is the only one allowed to read and write the table. Modules share no foreign key: they refer to each other by a plain identifier, and talk through public APIs or in-process events.
- **Personal data** follows the GDPR meaning: data that relates to an identifiable person, directly or through an identifier. A row tied to a user id is **personal (pseudonymous)**, even if its content looks harmless, because the user id points to an account in Keycloak.
- **User identifier** (`user_id`): the `sub` claim of the Keycloak JWT. `platform` stores that opaque id and nothing else about the person.

## Summary

| # | Data | Persona | Issue | Owner module | Personal | Retention (*proposal*) |
|---|---|---|---|---|---|---|
| 1 | Alert rule (clean grid) | Citizen | U2 | `automation` | Yes | Until the user deletes it |
| 2 | In-app notification | Citizen, official | U2, U16 | `delivery` | Yes | 90 days |
| 3 | Chosen usage | Citizen | U4 | `advisory` | Yes | Until the user deletes it |
| 4 | Usage log entry | Citizen | U6 | `advisory` | Yes | 24 months, or until the user deletes it |
| 5 | Chosen region (*if U3 is kept*) | Citizen | U3 | `advisory` | Yes | Until changed or deleted by the user |
| 6 | API key | DevOps | U10 | `automation` | Yes | Until revoked, then 30 days |
| 7 | API key daily counter | DevOps | U10 | `automation` | Yes | 13 months |
| 8 | Webhook | DevOps | U9 | `automation` | Yes (the URL may identify) | Until the user deletes it |
| 9 | Webhook delivery log | DevOps | U9 | `delivery` | Yes | 90 days |
| 10 | Target | Regional official | U13 | `regional` | Author yes, content no | Until the user deletes it |
| 11 | Target progress history | Regional official | U13 | `regional` | Yes (follows the target) | Same as its target |
| 12 | Rule state (last crossing) | Citizen, DevOps, official | P8, U16 | `automation` | Yes | Same as its rule, webhook or target (deleted on `TargetDeleted`, see "Deleting data") |

The W5 task names "subscriptions and limits". The issues have no separate subscription: a citizen's subscription **is** the alert rule with its threshold (row 1), and a DevOps subscription is a webhook (row 8). No extra table.

## 1. Citizen (`citizen`)

Stores nothing: traffic light [F5], today's mix [U1], cleanest hours map [U5] (they only read grid data).

| Data | Fields (from the issue) | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **1. Alert rule** (U2) | `user_id`, threshold (gCO2eq/kWh, 1 to 1000), active flag, dates | Notify when national CO2 crosses below the user's limit. Rule is stored, then evaluated on each new snapshot. At most 5 rules per user | `automation` | Until the user deletes it | Yes |
| **2. Notification** (U2, U16) | `user_id`, type (`clean-grid`, `target-pace`), source id (rule or target, plain reference), short message, created at, read flag. Generic on purpose: U2 notifies an intensity and a threshold, U16 a current value, an expected value and a gap, and both fit in the message | The in-app channel: header badge and notification box. First channel of `delivery`, reused by U16. DevOps does not use it: U9 delivers over HTTP (row 9) | `delivery` | 90 days, read or not | Yes |
| **3. Chosen usage** (U4) | `user_id`, label, duration (minutes), optional deadline. Defaults proposed: washing machine 2 h, dishwasher 3 h, tumble dryer 1 h 30, EV charge 6 h | Compute the best slot of the next 24 h from the typical profile | `advisory` | Until the user deletes it | Yes: tied to the user id, describes their habits |
| **4. Usage log entry** (U6) | `user_id`, usage, start (UTC), duration. **The CO2 value is not stored**: it is computed at read time from grid data, so the consolidated value replaces the provisional one when the batch lands | Show at which CO2 level the user consumed, with a weekly summary | `advisory` | 24 months, then deleted. The user can delete entries at any time. Entries in the far future or older than a year are refused | Yes: reveals when someone is at home and what they run |
| **5. Chosen region** (U3, *proposed*) | `user_id`, region code (`code_insee_region`, one of 12, picked from a list). Unknown code refused (400) | Show the regional picture next to the national one. The traffic light stays national | `advisory` | Until changed. Deleting it erases the row (acceptance criterion of U3) | Yes, because it is linked to the user id. The code alone is not: it names one of 12 regions. **No geolocation, no address, no postal code** |

## 2. DevOps (`devops`)

Stores nothing: carbon-intensity API [U7] (it only authenticates with the key of row 6) and best window [U8] (duration and deadline come in the request, nothing is saved). Dropped: job savings report, which would have stored job records.

| Data | Fields (from the issue) | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **6. API key** (U10) | key id, `user_id`, name, prefix (`2bg_ab12…`), **SHA-256 hash of the key** (never the key), created at, revoked at, last used at. At most 5 active keys per user | Authenticate the user's programmatic calls on `/api/v1/public/**` | `automation` | Until revoked. A revoked row is kept 30 days for audit (who revoked which key and when), then deleted. An unknown key answers 401 anyway, so this is not needed for the check | Yes. The full key is shown once at creation and appears nowhere in the database or the logs |
| **7. API key daily counter** (U10) | key id, UTC day, call count | Enforce the daily quota (429 with `Retry-After` above it) and show "calls today / quota". The quota (1,000 a day) is a property, not a column | `automation` | 13 months of daily rows (about 400 rows per key, small enough not to aggregate) | Yes, it follows a key owned by a user. Counts only: no URL, no IP, no payload |
| **8. Webhook** (U9) | webhook id, `user_id`, target URL, condition (below or above a threshold, or colour change), scope, **signing secret**, active flag, consecutive failure count. At most 10 per user | Call the user's URL when the condition becomes true. Disabled after 10 consecutive failures | `automation` | Until the user deletes it | Yes. The URL may carry a token or name a company: treat it like a credential and never log it in clear. The secret must be **recoverable** (HMAC-SHA256 signing needs it), so it cannot be hashed like an API key: shown once at creation, never returned afterwards. **Risk to list in W4:** a database leak would let someone forge signed webhook calls to the user's URL |
| **9. Webhook delivery log** (U9) | delivery id (sent as `X-2bGreen-Delivery`, so the receiver can deduplicate), **`user_id`** (copied from the webhook, so the log stays reachable for a purge after the webhook is deleted), webhook id (plain reference, may point to a deleted webhook), attempt number, timestamp (UTC), outcome, HTTP status or error kind | One row per attempt: prove a call was made once per crossing and let the user debug a failing URL | `delivery` | 90 days | Yes. **Never store the sent payload or the response body**, only the status |

## 3. Regional official (`regional_official`)

Stores nothing: regional dashboard [U11], trends [U12], comparison [U14], estimated CO2 [U15] (they only read grid data). Dropped: timeline annotations and async report.

**The official's region is not stored in `platform`.** U11 puts it in a Keycloak attribute, `region_code`, exposed in the token by a mapper, and the official cannot change it. `platform` reads it from the token on each call. So there is no "chosen region" row for this persona: this replaces what the first version of this document said. Consequence for the team: that attribute is user data held by **Keycloak** (and exported in `infra/keycloak/2bgreen-realm.json`), to be listed in the data strategy (W2) and the risk matrix (W4).

| Data | Fields (from the issue) | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **10. Target** (U13) | target id, `user_id` (author), `region_code` (must equal the token's), indicator (renewable share, coverage rate, consumption drop, estimated CO2), reference year, reference value (computed at creation), target value, deadline, tolerance (in points, used by U16, **optional: no tolerance means the pace alert is off**) | Set a target and follow progress against it | `regional` | Until the user deletes it | The author reference is personal. The content (a number for a region) is not |
| **11. Target progress history** (U13) | target id, date, value, status (ahead, on track, late). One row per day, added after the regional resync | Draw the progress chart over time | `regional` | Same as its target (deleted with it) | Yes, it follows the target |

## 4. Shared by several personas

| Data | Fields | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **12. Rule state** (P8, U16) | rule, webhook or target id (plain reference), state (below or above, on track or late), last crossing timestamp, last snapshot seen | The memory of the rule engine: one notification per crossing, a replayed Kafka event creates no duplicate (acceptance criteria of P8, U2, U9, U16) | `automation` | Same as what it follows | Yes |

Row 2 (notifications) sits in `delivery` because that module sends them. `delivery` and `automation` share no table: a notification or a delivery row refers to its rule by id only.

## Not stored by `platform`

- **Name, e-mail, password, roles**: Keycloak. `platform` reads the roles from the token and keeps only the `sub`.
- **The official's region** (`region_code`): a Keycloak attribute, see section 3.
- **Tokens**: the browser's session storage (F6). Not in any database.
- **Geolocation, postal address, IP address, device data**: not collected.
- **Anything about a user in `grid-intelligence`** and in the Kafka topics: grid data is public and the events carry no user id.
- **E-mail as a notification channel**: U2 asks whether to add it. If yes, an address would have to be read from Keycloak or stored here, and this inventory changes. Today the proposal is the in-app channel only.

## Deleting data

Two kinds of deletion, and in both cases `platform` is the one that must trigger it, because no module hears about what happens in another one (no foreign key between modules).

- **A target is deleted** (by its author, or with the account). `regional` publishes an in-process `TargetDeleted` event. `automation` handles it and deletes the rule state of row 12 that follows the target. Row 11 (progress history) is in `regional` and goes with the target directly.
- **An account is deleted.** Keycloak does not tell `platform` that a user is gone, so the trigger has to be in `platform`. Realistic option: a "delete my account" endpoint in `platform` (`DELETE /api/v1/me`) that publishes an in-process `UserDeleted` event, then deletes the user through the Keycloak admin API. Each module handles `UserDeleted` for its own tables: rows 1 to 12 all carry a `user_id` (row 9 included, for this reason). It is the same mechanism as `TargetDeleted`. Deleting an account directly in the Keycloak console would leave the rows behind: acceptable for the project, to note in the risk matrix.
- Row-level deletion by the user already exists in the issues (U3, U6, and the lists of rules, webhooks, keys, targets).

Right of access (*optional, not in any issue*): a `GET /api/v1/me/data` that gathers the user's rows from every module would answer access and portability. It needs the same per-module contract as `UserDeleted`.

Legal basis (*proposal*): the data is processed to provide the service the user asked for (contract), not for tracking. Nothing is used for analytics or shared with a third party except the webhook calls the user configured themselves.

## How P7 implements it

The schema is built by the Flyway migrations of `platform` (`platform/src/main/resources/db/migration`, one file per module). A table is prefixed with the name of its owner module. `UserDataSchemaTests` fails if a table is not listed here, or if this list names a table that does not exist.

| # | Table | Entity (in `<module>.domain`) |
|---|---|---|
| 1 | `automation_alert_rule` | `AlertRule` |
| 2 | `delivery_notification` | `Notification` |
| 3 | `advisory_usage` | `Usage` |
| 4 | `advisory_usage_log_entry` | `UsageLogEntry` |
| 5 | `advisory_region_preference` | `RegionPreference` |
| 6 | `automation_api_key` | `ApiKey` |
| 7 | `automation_api_key_daily_counter` | none: one atomic statement of `ApiKeyRepository` |
| 8 | `automation_webhook` | `Webhook` |
| 9 | `delivery_webhook_delivery` | `WebhookDelivery` |
| 10 | `regional_target` | `Target` |
| 11 | `regional_target_progress` | `TargetProgress` |
| 12 | `automation_rule_state` | `RuleState` |

- **No foreign key between modules.** Cross-module links (a rule state pointing at a target, a notification at a rule, a delivery row at a webhook) are plain ids, so deletions between modules go through events (see "Deleting data"). The only two foreign keys stay inside a module: a counter follows its key, a progress row follows its target, both with `ON DELETE CASCADE`.
- **Every row is reachable from a user id.** Ten tables have a `user_id` column (the Keycloak `sub`, as text). Rows 7 and 11 do not: they are deleted with their parent row, which has it. Each repository has a `deleteAllOfUser` for the account deletion.
- **Choices made by P7 on points the inventory left open:**
  - Rows 2 and 9 stay two tables, one per channel: their columns have almost nothing in common.
  - The read flag of a notification is a timestamp, `read_at`, empty while unread.
  - The optional deadline of a usage (U4) is a time of day in Paris time, `finish_before`: a stored usage recurs, so it cannot be a date. *To confirm with U4.*
  - A usage log entry copies the label of its usage instead of pointing at it, so deleting a usage keeps the log.
  - The values of `state` in row 12 are left to P8.
- **Not in the schema:** the limits per user (5 rules in U2, 10 webhooks in U9, 5 active keys in U10) are for the services, and the retention purges have their queries in the repositories but no scheduled job yet.

## Open questions for the review

A colleague's review answered some of these. Marked *proposed*: the team still decides.

1. Retention values (24 months, 90 days, 30 days, 13 months): *proposed: fine as they are.*
2. Is "my region" [U3] kept? If not, row 5 disappears. *Team's call.*
3. A target whose author leaves: *proposed: delete it, and note that a real deployment would reassign it to the region.*
4. Pace alert (U16): *proposed: opt-in, a missing tolerance means off, so no extra flag.* This is a gap in U16 itself, which should say so.
5. Account deletion: *proposed: one event per module, triggered by a `DELETE /api/v1/me` endpoint in `platform`.*
6. Application access logs (IP, path): *proposed: none.* If added, they need a row with a short retention.
7. In-app notifications: *proposed: 90 days whether read or not.*
