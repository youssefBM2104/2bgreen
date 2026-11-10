# User data inventory (W5)

What `platform` stores about its users, per persona: why, which module owns it, how long we keep it, and whether it is personal data. This is the input of P7 (schema of `platform`): every table of P7 must come from a row below, and every row below must end up in a table.

**Status: draft for team review.** Retention periods and the personal-data calls are proposals (marked *proposal*): nobody has validated them yet. The review is an acceptance criterion of issue #22.

## Scope and conventions

- Only data **created by the users** is listed here. Grid data (ODRÉ, `speed_snapshot`, `batch_snapshot`) is public and lives in `grid-intelligence`: see `docs/data/eco2mix-*.md`.
- Everything below lives in schema `platform`, written by the role `platform_app`. `grid-intelligence` never sees it (it knows no user).
- **Owner module** = the Spring Modulith module that is the only one allowed to read and write the table. Modules share no foreign key: they refer to each other by a plain identifier, and talk through public APIs or in-process events.
- **Personal data** follows the GDPR meaning: data that relates to an identifiable person, directly or through an identifier. A row tied to a user id is **personal (pseudonymous)**, even if its content looks harmless, because the user id points to an account in Keycloak.
- **User identifier**: the `sub` claim of the Keycloak JWT. `platform` stores that opaque id and nothing else about the person. Name, e-mail and password stay in Keycloak (see "Not stored by platform").

## Summary

| # | Data | Persona | Owner module | Personal | Retention (*proposal*) |
|---|---|---|---|---|---|
| 1 | Subscriptions and limits | Citizen, regional official | `automation` | Yes | While the rule exists |
| 2 | Chosen usages | Citizen | `advisory` | Yes | Until the user deletes it or the account is deleted |
| 3 | Usage log | Citizen | `advisory` | Yes | 24 months, then deleted |
| 4 | Chosen region | Citizen, regional official | `advisory` (citizen), `regional` (official) | Yes (linked to the user) | Until changed or the account is deleted |
| 5 | API keys | DevOps | `automation` | Yes | Until revoked, then 30 days |
| 6 | Key usage counters | DevOps | `automation` | Yes | Current period + 13 months of monthly totals |
| 7 | Webhook rules | DevOps | `automation` | Yes (the URL may identify) | Until deleted by the user |
| 8 | Delivery log | DevOps, citizen, official | `delivery` | Yes | 90 days |
| 9 | Targets | Regional official | `regional` | Yes (the author), content not | Until deleted by the user |
| 10 | Alert rules | Citizen, regional official | `automation` | Yes | Until deleted by the user |
| 11 | Alert state (last crossing) | Citizen, DevOps, official | `automation` | Yes | Same as its rule |

## 1. Citizen (`citizen`)

Features: traffic light [F5], today's mix [U1], clean-grid alert [U2], best slot [U4], cleanest hours map [U5], usage log [U6], my region [U3] (proposed, not confirmed).

Traffic light, today's mix and cleanest hours map store **nothing** about the user: they only read grid data.

| Data | Fields | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **Chosen usages** (U4) | user id, usage type (dishwasher, washing machine, EV charge, ...), optional duration | Recommend the best slot for the usages the user cares about | `advisory` | Until the user removes it, or the account is deleted | Yes: tied to the user id, and it describes their habits |
| **Usage log** (U6) | user id, usage type, timestamp (UTC) of the usage, CO2 intensity at that time (computed, from grid data) | Show at which CO2 level the user consumed, compared with the daily average | `advisory` | 24 months, then deleted. After that the user's own comparison loses little value | Yes: it reveals when someone is at home and what they run |
| **Chosen region** (U3, *if kept*) | user id, region code (INSEE, picked from a list) | Show the regional mix next to the national picture | `advisory` | Until changed or the account is deleted | Yes, because it is linked to the user id. The code alone is not: it names one of 12 regions, so it is not a location. **No geolocation, no address, no postal code** |
| **Alert rule** (U2) | user id, kind `clean-grid`, CO2 threshold (gCO2eq/kWh), channel (see 8), enabled flag | Notify when CO2 falls below the user's limit | `automation` | Until the user deletes it | Yes |
| **Subscription and limit** (U2) | user id, subscribed (yes/no), limit | The "subscription" of the catalog: the user's consent to be notified, with their limit. In practice it is the alert rule above | `automation` | While the rule exists | Yes |

Notes:
- Usage log timestamps are UTC (the project rule). The CO2 value is stored at the time of writing, as a copy, so the log stays readable if grid data is later revised (consolidated to definitive). *To confirm in P7*: copy the value, or recompute at read time.
- **Open question for the team:** is 24 months the right retention for the usage log, and should the user be able to export or purge it themselves? The catalog drops "history export" for DevOps, so no export feature is planned.

## 2. DevOps (`devops`)

Features: carbon-intensity API [U7], best window for a job [U8], conditional webhooks [U9], keys and quotas [U10]. Dropped: job savings report (it would have stored job records).

U7 and U8 read grid data. U8 takes its inputs (duration, deadline) in the request and **stores nothing**.

| Data | Fields | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **API key** (U10) | key id, user id, label, **hash of the key** (never the key itself), short visible prefix, quota per period, created at, revoked at | Authenticate the DevOps user's programmatic calls and limit them | `automation` | Until revoked. A revoked key row is kept 30 days (for audit and so a revoked key keeps answering 401, not "unknown"), then deleted | Yes: tied to the user id. The secret is shown once at creation and cannot be recovered |
| **Key usage counter** (U10) | key id, period (day or month), call count | Enforce the quota (429 above it) and show usage | `automation` | Current period at full detail. Monthly totals kept 13 months, to show a year-on-year view, then deleted | Yes, since it follows a key owned by a user. It holds counts only: no URL, no IP, no payload |
| **Webhook rule** (U9) | rule id, user id, target URL, signing secret, condition (CO2 below X for N consecutive readings), enabled flag | Call the user's URL when the condition holds | `automation` | Until the user deletes it | Yes. The URL may contain a token or identify a company: treat it like a credential, do not log it in clear. The signing secret is stored so `platform` can sign calls; it is never returned by the API after creation |
| **Delivery log** | see row 8 below | Prove a webhook was sent, debug failures | `delivery` | 90 days | Yes |
| **Alert state** | see row 11 below | Anti-bounce: notify once per crossing | `automation` | Same as its rule | Yes |

Notes:
- **Key hashing** is a design decision for P7/U10: store a hash, compare on each call. The key id (or prefix) lets the user tell their keys apart.
- Request logs of the API (IP, path, timestamp) are **not** stored by the application. If the team adds access logs for monitoring, they hold personal data (IP) and must be added here with a short retention.

## 3. Regional official (`regional_official`)

Features kept: regional dashboard [U11], regional trends [U12], targets and progress [U13], region comparison [U14], estimated regional CO2 [U15], target-pace alert [U16]. Dropped: timeline annotations, async report (they would have stored annotations, report requests and files).

Dashboard, trends, comparison and estimated CO2 only read grid data.

| Data | Fields | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **Chosen region** (U11, U13) | user id, region code | Open the dashboard on the official's own region and attach targets to it | `regional` | Until changed or the account is deleted | Yes, tied to the user id. The role itself (an official of region X) is already identifying in practice |
| **Target** (U13) | target id, user id (author), region code, indicator (e.g. renewable share), target value, deadline year, created at | Set a target and compute progress against grid history | `regional` | Until deleted by the user. A target is institutional content: if the official leaves, the team must decide whether it is deleted or kept for the region (see open question) | The author reference is personal. The content (a number for a region) is not |
| **Alert rule, pace type** (U16) | rule id, user id, target id (plain reference, no foreign key), allowed gap, channel | Notify when the region falls behind its target's trajectory | `automation` | Until deleted by the user, **and** deleted with its target | Yes |
| **Alert state** | see row 11 below | Anti-bounce | `automation` | Same as its rule | Yes |

## 4. Data shared by several personas

| Data | Fields | Purpose | Owner | Retention | Personal |
|---|---|---|---|---|---|
| **Delivery log** (row 8) | delivery id, rule id, user id, channel (webhook, in-app), timestamp (UTC), outcome (sent, failed), HTTP status or error kind, attempt number, id of the snapshot that triggered it (`scope` + `observedAt`) | Journal every notification (acceptance criterion of P8), prove a single delivery per crossing, help the user debug a failing URL | `delivery` | **90 days** then deleted. Long enough to debug, short enough not to become a history of the user's behaviour | Yes. **Never store the payload or the response body**, only the status |
| **Alert state** (row 11) | rule id, state (below / above), last crossing timestamp, last snapshot id seen | Rule engine memory: notify once per crossing, and ignore a replayed Kafka event | `automation` | Same as its rule | Yes (follows the rule) |

The delivery log lives in `delivery` because that module sends the notification. `delivery` and `automation` share no table: the log refers to the rule by id only.

## Not stored by `platform`

- **Name, e-mail, password, roles**: Keycloak. `platform` reads the roles from the token on each call and keeps only the `sub`.
- **Tokens** (access, refresh): the browser's session storage (F6). Not in any database.
- **Geolocation, postal address, IP address, device data**: not collected.
- **Anything about a user in `grid-intelligence`** and in the Kafka topics: the grid data is public and the events carry no user id.

## Deleting an account

When a user is deleted in Keycloak, all rows carrying their user id must go: usages, usage log, region, keys and counters, webhook rules, alert rules and states, delivery log, targets' author reference. This is a requirement for P7 (every table with a user id must be reachable for a purge). Mechanism to decide: a `UserDeleted` in-process event that each module handles for its own tables, or a manual purge for the project. *Not designed yet.*

## What P7 should take from this

- One table per row of the summary, grouped by module: `advisory` (2, 3, 4-citizen), `automation` (1, 5, 6, 7, 10, 11), `regional` (4-official, 9), `delivery` (8).
- Row 1 (subscriptions and limits) and row 10 (alert rules) are the same thing for the citizen's clean-grid alert. P7 can model it as **one** `alert_rule` table with a `kind` (`clean-grid`, `pace`) rather than two. Open for P7.
- Region choice has two owners because the two personas use it differently. If the team prefers a single table, it belongs in `advisory` and `regional` would read it through a public API.
- Every table has a user id column of the same type (the Keycloak `sub`, text/uuid) and no foreign key to another module's table.
- Retention needs jobs: usage log 24 months, delivery log 90 days, revoked keys 30 days, counters 13 months. A scheduled purge in `platform` is part of P7 or a follow-up.

## Open questions for the review

1. Retention values (24 months, 90 days, 30 days, 13 months): acceptable, or too long or short?
2. Is "my region" [U3] kept? If not, remove the citizen region row.
3. Targets when their author leaves: delete or keep for the region?
4. Usage log: copy the CO2 value at write time, or recompute at read time?
5. Account deletion: event per module, or manual purge?
6. Do we add application access logs? If so they need a row (IP, short retention).
