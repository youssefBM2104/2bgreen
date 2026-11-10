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
| 2 | In-app notification | Citizen, DevOps, official | U2, U9, U16 | `delivery` | Yes | 90 days |
| 3 | Chosen usage | Citizen | U4 | `advisory` | Yes | Until the user deletes it |
| 4 | Usage log entry | Citizen | U6 | `advisory` | Yes | 24 months, or until the user deletes it |
| 5 | Chosen region (*if U3 is kept*) | Citizen | U3 | `advisory` | Yes | Until changed or deleted by the user |
| 6 | API key | DevOps | U10 | `automation` | Yes | Until revoked, then 30 days |
| 7 | API key daily counter | DevOps | U10 | `automation` | Yes | 13 months |
| 8 | Webhook | DevOps | U9 | `automation` | Yes (the URL may identify) | Until the user deletes it |
| 9 | Webhook delivery log | DevOps | U9 | `delivery` | Yes | 90 days |
| 10 | Target | Regional official | U13 | `regional` | Author yes, content no | Until the user deletes it |
| 11 | Target progress history | Regional official | U13 | `regional` | Yes (follows the target) | Same as its target |
| 12 | Rule state (last crossing) | Citizen, DevOps, official | P8, U16 | `automation` | Yes | Same as its rule, webhook or target |

The W5 task names "subscriptions and limits". The issues have no separate subscription: a citizen's subscription **is** the alert rule with its threshold (row 1), and a DevOps subscription is a webhook (row 8). No extra table.

## 1. Citizen (`citizen`)

Stores nothing: traffic light [F5], today's mix [U1], cleanest hours map [U5] (they only read grid data).

| Data | Fields (from the issue) | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **1. Alert rule** (U2) | `user_id`, threshold (gCO2eq/kWh, 1 to 1000), active flag, dates | Notify when national CO2 crosses below the user's limit. Rule is stored, then evaluated on each new snapshot. At most 5 rules per user | `automation` | Until the user deletes it | Yes |
| **2. Notification** (U2) | `user_id`, timestamp, intensity, threshold crossed, read flag | The in-app channel: header badge and notification box. First channel of `delivery`, reused by U9 and U16 | `delivery` | 90 days, read or not | Yes |
| **3. Chosen usage** (U4) | `user_id`, label, duration (minutes), optional deadline. Defaults proposed: washing machine 2 h, dishwasher 3 h, tumble dryer 1 h 30, EV charge 6 h | Compute the best slot of the next 24 h from the typical profile | `advisory` | Until the user deletes it | Yes: tied to the user id, describes their habits |
| **4. Usage log entry** (U6) | `user_id`, usage, start (UTC), duration. **The CO2 value is not stored**: it is computed at read time from grid data, so the consolidated value replaces the provisional one when the batch lands | Show at which CO2 level the user consumed, with a weekly summary | `advisory` | 24 months, then deleted. The user can delete entries at any time. Entries in the far future or older than a year are refused | Yes: reveals when someone is at home and what they run |
| **5. Chosen region** (U3, *proposed*) | `user_id`, region code (`code_insee_region`, one of 12, picked from a list). Unknown code refused (400) | Show the regional picture next to the national one. The traffic light stays national | `advisory` | Until changed. Deleting it erases the row (acceptance criterion of U3) | Yes, because it is linked to the user id. The code alone is not: it names one of 12 regions. **No geolocation, no address, no postal code** |

## 2. DevOps (`devops`)

Stores nothing: carbon-intensity API [U7] (it only authenticates with the key of row 6) and best window [U8] (duration and deadline come in the request, nothing is saved). Dropped: job savings report, which would have stored job records.

| Data | Fields (from the issue) | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **6. API key** (U10) | key id, `user_id`, name, prefix (`2bg_ab12…`), **SHA-256 hash of the key** (never the key), created at, revoked at, last used at. At most 5 active keys per user | Authenticate the user's programmatic calls on `/api/v1/public/**` | `automation` | Until revoked. A revoked row is kept 30 days so the key keeps answering 401, then deleted | Yes. The full key is shown once at creation and appears nowhere in the database or the logs |
| **7. API key daily counter** (U10) | key id, UTC day, call count | Enforce the daily quota (429 with `Retry-After` above it) and show "calls today / quota". The quota (1,000 a day) is a property, not a column | `automation` | 13 months of daily rows (about 400 rows per key, small enough not to aggregate) | Yes, it follows a key owned by a user. Counts only: no URL, no IP, no payload |
| **8. Webhook** (U9) | webhook id, `user_id`, target URL, condition (below or above a threshold, or colour change), scope, **signing secret**, active flag, consecutive failure count. At most 10 per user | Call the user's URL when the condition becomes true. Disabled after 10 consecutive failures | `automation` | Until the user deletes it | Yes. The URL may carry a token or name a company: treat it like a credential and never log it in clear. The secret must be **recoverable** (HMAC-SHA256 signing needs it), so it cannot be hashed like an API key: shown once at creation, never returned afterwards |
| **9. Webhook delivery log** (U9) | delivery id (sent as `X-2bGreen-Delivery`, so the receiver can deduplicate), webhook id, attempt number, timestamp (UTC), outcome, HTTP status or error kind | One row per attempt: prove a call was made once per crossing and let the user debug a failing URL | `delivery` | 90 days | Yes. **Never store the sent payload or the response body**, only the status |

## 3. Regional official (`regional_official`)

Stores nothing: regional dashboard [U11], trends [U12], comparison [U14], estimated CO2 [U15] (they only read grid data). Dropped: timeline annotations and async report.

**The official's region is not stored in `platform`.** U11 puts it in a Keycloak attribute, `region_code`, exposed in the token by a mapper, and the official cannot change it. `platform` reads it from the token on each call. So there is no "chosen region" row for this persona: this replaces what the first version of this document said. Consequence for the team: that attribute is user data held by **Keycloak** (and exported in `infra/keycloak/2bgreen-realm.json`), to be listed in the data strategy (W2) and the risk matrix (W4).

| Data | Fields (from the issue) | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **10. Target** (U13) | target id, `user_id` (author), `region_code` (must equal the token's), indicator (renewable share, coverage rate, consumption drop, estimated CO2), reference year, reference value (computed at creation), target value, deadline, tolerance (in points, used by U16) | Set a target and follow progress against it | `regional` | Until the user deletes it | The author reference is personal. The content (a number for a region) is not |
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

## Deleting an account

When a user is deleted in Keycloak, every row with their `user_id` must go: rows 1 to 12. This is a requirement for P7: each table with a user id must be reachable for a purge. The mechanism is not designed: an in-process `UserDeleted` event handled by each module for its own tables, or a manual purge. Several issues already ask that the user can delete their own data (U3, U6); row-level deletion by the user exists, deleting the whole account does not yet.

## What P7 should take from this

- One table per row of the summary, grouped by module: `advisory` (3, 4, 5), `automation` (1, 6, 7, 8, 12), `regional` (10, 11), `delivery` (2, 9).
- Every table has a `user_id` column of the same type (the Keycloak `sub`) and no foreign key to another module's table. Cross-module links (a rule state pointing at a target, a notification at a rule) are plain ids.
- Rows 2 and 9 are two logs for two channels (in-app, HTTP). P7 may unify them in one `delivery` table with a channel column, or keep two.
- Retention needs a scheduled purge in `platform`: notifications and delivery log 90 days, usage log 24 months, counters 13 months, revoked keys 30 days.
- Constraints stated in the issues belong in the schema or the services: 5 rules per user (U2), 10 webhooks (U9), 5 active keys (U10).

## Open questions for the review

1. Retention values (24 months, 90 days, 30 days, 13 months): acceptable, or too long or short? None is set by the issues.
2. Is "my region" [U3] kept? If not, row 5 disappears.
3. A target whose author leaves: deleted, or kept for the region?
4. Pace alert (U16): is it on for every target, or does the official opt in? U16 adds a tolerance to the target form and no separate rule, so today it looks always on. If opt-in, a flag goes on row 10.
5. Account deletion: event per module, or manual purge?
6. Do we add application access logs (IP, path)? If so they need a row with a short retention.
7. In-app notifications: 90 days whether read or not, or keep unread ones longer?
