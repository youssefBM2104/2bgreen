# Contributing to 2bGreen

## Issues

Every piece of work is a GitHub issue titled `<code> — <title>` (`G6 — Batch régional ...`), written in French, with
a "Dépend de" section. An issue is ready when everything it depends on is closed. Codes: K contracts, R repo and
infrastructure, T tests, F frontend and CI, P `platform` backend, D grid ingestion, stream and read API, G grid data
after slice 1, U user features, W written deliverables. A code is never reused.

## Branches

One branch per issue, from an up-to-date `main`:

```
<type>/<issue number>-<code>-<short-slug>
feat/28-g6-batch-regional
docs/22-w5-user-data
```

## Commits

[Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <description>`, description in French.

- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`.
- Scopes: `grid`, `platform`, `frontend`, `infra`, `api`. Leave the scope out when a change spans several.
- Put the issue code in the description when it helps: `feat(grid): batch régional de resynchronisation de cons-def (G6)`.

## Pull requests

- One PR per issue, linked with `Closes #<number>`.
- Merge only with a green CI and one approving review from another member.
- A change under `docs/contracts/` (Kafka events) or to an OpenAPI file in `docs/api/` tags all three members.
- A structuring decision gets an ADR in `docs/adr/` (template `0000-template.md`), in the same PR. An accepted ADR is
  not rewritten: change its status, or add a dated "Update" section.

## Definition of done

- [ ] The issue's acceptance criteria are met.
- [ ] Tests cover the change and pass locally (commands below).
- [ ] CI is green.
- [ ] No test calls the live ODRÉ API: use the fixtures in `grid-intelligence/src/test/resources/odre/` or
      `FakeOdreServer`. The CI job `no-live-odre` checks it.
- [ ] No secret committed. The passwords in the repository are for local development only.
- [ ] Docs updated: README if running or checking the app changed, contract or ADR if a decision changed.
- [ ] Reviewed by another member.

## Running the checks

Backends, from the module directory (the tests read `../infra/postgres/init`); Docker must be running for
Testcontainers:

```bash
cd platform && ./mvnw -B verify
cd grid-intelligence && ./mvnw -B verify
```

Frontend:

```bash
cd frontend && npm run lint && npm run test:ci && npm run build
```

Building `platform` rewrites `build/generated-spring-modulith/javadoc.json`; do not commit that change
(`git checkout -- build/`).

## Code style

`.editorconfig` at the root sets the basics: UTF-8, LF, tabs for Java and Maven files (Spring style, as in the
code), 4 spaces for SQL, 2 spaces for TypeScript, HTML, SCSS, YAML and JSON.
