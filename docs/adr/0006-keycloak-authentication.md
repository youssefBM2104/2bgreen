# ADR-006: People log in with Keycloak (OIDC and PKCE), `platform` checks the tokens

## Status

Accepted (2026-10-14).

## Context

2026-10-14. Three personas with different rights (`citizen`, `devops`, `regional_official`) use one Angular app. The
app runs in the browser, so it cannot keep a secret. Authentication is not what the product is about, and writing
it ourselves (password storage, reset, sessions) is a security risk. DevOps scripts are a separate case (ADR-005).

## Options considered

1. **Custom login in `platform`** (users and password hashes in schema `platform`). Full control, but all the
   security code is ours to get right.
2. **A hosted provider** (Auth0, Okta...). Nothing to run, but an account and an internet connection for every
   developer and every environment.
3. **Backend-for-frontend**: `platform` does the login as a confidential client and keeps tokens out of the browser.
   Safer against XSS, but sessions, CSRF and a more complex `platform`.
4. **Keycloak in Compose, authorization code with PKCE, `platform` as a JWT resource server (chosen).**

## Decision

Option 4.

- Keycloak holds the users and the realm roles. Realm `2bgreen` is exported in `infra/keycloak/2bgreen-realm.json`
  and imported on first start, with one test user per persona.
- The frontend is a public client (`2bgreen-frontend`) using code + PKCE: a stolen authorization code is useless
  without the per-login secret. Route guards are for comfort only; the real check is in `platform`.
- `platform` downloads the signing keys once and checks signature, issuer and expiry of each token itself. It reads
  the roles from `realm_access.roles` and endpoints use `@PreAuthorize`. Stateless, no CSRF, CORS only for
  `http://localhost:4200`.
- Data that belongs to the identity also lives in Keycloak: the region of an official is the user attribute
  `region_code`, set by an administrator and copied into the token.
- `grid-intelligence` has no authentication: it is only called by `platform`.

## Consequences

- Good: no password ever reaches our apps; adding a role or a user needs no code.
- Good: no call to Keycloak per request, so a Keycloak outage only blocks new logins.
- Bad: tokens are kept in the browser's `sessionStorage` (library default), so an XSS flaw could read them. Accepted,
  with no `localStorage` and short-lived tokens; risk-matrix entry.
- Bad: one more container, and its realm must be re-exported by hand after a change; an existing volume does not
  re-import it.
- Risk: `grid-intelligence` must stay unreachable from outside the internal network; risk-matrix entry.
