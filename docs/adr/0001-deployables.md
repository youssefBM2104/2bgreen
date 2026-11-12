# ADR-001: Two backends, each owning one kind of data, plus the frontend

## Status

Accepted (2026-10-14).

## Context

2026-10-14. 2bGreen has two very different jobs. One is to collect, clean and store French electricity data (ODRÉ
API, every 15 minutes, plus a daily history resync). The other is to serve three personas (citizen, DevOps, regional
official) and to store their own data: alerts, API keys, targets. The team is three developers with a few weeks:
every extra deployable costs time to build, run and test.

## Options considered

1. **One Spring application.** Simplest to run. But nothing stops user code from reading ODRÉ rows or grid code from
   reading users: the two domains would mix, and an ODRÉ problem (slow export, quota) would run in the same process
   as the user features.
2. **One microservice per bounded context** (Grid Intelligence, Citizen Advisory, Carbon-Aware API, Regional
   Transition, Delivery), or even per job (poller, stream, batch). Clean boundaries, but five to seven deployables,
   network calls between them, distributed failures and one database per service, for a volume of about one reading
   per scope every 15 minutes. Far too much for the team and the load.
3. **Two backends split on the data they own, each internally modular (chosen).** Grid Intelligence gets its own
   deployable; the four contexts built on users share one, as modules.

## Decision

Option 3, three deployables:

| Deployable | Owns | Inside |
|---|---|---|
| `grid-intelligence` (Spring Boot, port 8081) | Electricity data, schema `grid` | Ports and adapters (`domain`, `application`, `infrastructure`), checked by ArchUnit. Its jobs are Spring profiles of the same app: `poller`, `stream`, `batch` |
| `platform` (Spring Boot, port 8080) | Users and product data, schema `platform` | Modular monolith, one Spring Modulith module per bounded context (below) |
| `frontend` (Angular) | Nothing | One area per persona; calls only `platform` and Keycloak |

| Bounded context | Where |
|---|---|
| Grid Intelligence | `grid-intelligence` |
| Citizen Advisory | `platform`, module `advisory` |
| Carbon-Aware API | `platform`, module `automation` |
| Regional Transition | `platform`, module `regional` |
| Delivery | `platform`, module `delivery` |

The deployable boundary is the strongest one: Grid Intelligence knows nothing about users, and the contexts of
`platform` know nothing about ODRÉ. Inside `platform`, the contexts are modules: they talk through public APIs or
in-process events, never over the network, and `ModularityTests` fails the build on an illegal dependency. Each
module owns its own tables (prefix `advisory_`, `automation_`, `regional_`, `delivery_`), with no foreign key between
modules.

Docker Compose runs the three deployables and the infrastructure. The batch runs as a second container of the same
image (`docker compose --profile batch`), so a long history load never slows the speed layer.

## Consequences

- Good: the boundary between grid data and users is physical. An ODRÉ or batch problem cannot take the user
  features down, and the two apps can be worked on in parallel.
- Good: a modular monolith gives most of the separation of microservices for the cost of one deployable. A module
  could become a service later because its boundary is already tested.
- Bad: one network hop between the apps, so `platform` must handle `grid-intelligence` being down (`503
  GRID_UNAVAILABLE`), see ADR-002. Nothing is shared in code: values used by both (band limits) are written twice.
- Bad: the boundaries inside `platform` are only as strong as the module tests; a shared deployable means a shared
  release and a shared database pool.
- Bad: the `platform` domain is not framework-free (entities carry Spring Data JDBC annotations), unlike
  `grid-intelligence`. Accepted to keep the modules small.
- Risk: `grid-intelligence` has no authentication (ADR-006), so it must stay on the internal network.
