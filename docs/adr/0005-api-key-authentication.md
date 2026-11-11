# ADR-005: Authentication of DevOps scripts by API key

## Status

Accepted for the API-key path (built in U10, #42). The Keycloak path for humans (OIDC with PKCE, `platform` as a JWT
resource server) is already in the code and gets its own section in this ADR with F1′ (#2). The public endpoints
that use the key are U7 (#39).

## Context

DevOps users call 2bGreen from scripts and CI pipelines (carbon-intensity API U7, best window U8), not from a
browser. They log in to the DevOps page with Keycloak like everyone else, but a pipeline cannot do a PKCE login. It
needs a credential it can hold in a CI secret and send on every request. `platform` must:

- know which user a call belongs to, to count it against a daily quota (1,000 calls per key and per UTC day);
- let the user revoke a credential at once, without touching their Keycloak account;
- never store the credential in a form that could be replayed if the database leaked.

## Options considered

1. **OAuth2 client credentials in Keycloak.** One confidential Keycloak client per DevOps user (or per pipeline);
   the script trades the client id and secret for a short-lived JWT, and `platform` checks it like any other token.
   - Good: one token format, and expiry and signature come for free.
   - Bad: creating a client for every user means calling the Keycloak admin API from `platform` with an admin
     credential. Scripts need two calls (token, then request) and must handle the expiry. Revocation only takes
     effect when the current token expires, unless `platform` checks with Keycloak on every call. The quota still
     needs a counter in `platform`.
2. **API key stored in plain text.** Simple, but whoever reads the table (a backup, an SQL injection, a too-wide
   role) can call the API as every user.
3. **API key, hashed with bcrypt or Argon2.** A slow, salted hash protects short secrets that humans choose. It
   cannot be looked up: the filter would have to find the key by another column and then pay about 100 ms on every
   API call.
4. **API key, hashed with SHA-256 (chosen).**

## Decision

Option 4. A key is `2bg_` followed by 32 bytes from `SecureRandom` in URL-safe Base64, 47 characters in all. It is
shown once at creation. `automation_api_key` keeps its SHA-256 (hex, unique index) and its first 8 characters
(`2bg_ab12`), so the user can tell keys apart.

- **Why a fast hash is enough here.** bcrypt is slow on purpose, so that an attacker cannot try every likely password
  against a stolen hash. A key has 256 random bits and no likely values, so no dictionary exists and brute force over
  2^256 is out of reach at any hash speed. Salt is not needed either: two keys never share a value, so precomputed
  tables are useless.
- **Why it matters for speed.** The filter hashes the header and finds the row through the unique index, in one
  query. It does not scan the table, and it adds no measurable time to a call that also counts the quota.
- **A separate security chain for `/api/v1/public/**`.** It comes before the Keycloak chain and accepts only the
  `X-API-Key` header. A JWT alone gets a `401`, so a stolen browser token does not open the public API. There is no
  CORS on it, because the public API is not meant for a web page, and no session. A key does not open any other path:
  key management stays behind a Keycloak login with the `devops` role.
- **Revocation** sets `revoked_at`. Nothing is cached, so the next call is refused (`401`). An unknown key and a
  revoked key get the same answer, so the answer does not say which keys exist. A revoked row is kept 30 days for
  audit.
- **Quota.** One atomic `INSERT ... ON CONFLICT DO UPDATE ... WHERE call_count < quota RETURNING` per call. Concurrent
  calls can neither lose an increment nor pass the limit together, and a refused call is not counted. Above the quota
  the answer is `429` with `Retry-After`. Every accepted call carries `X-RateLimit-Limit`, `X-RateLimit-Remaining` and
  `X-RateLimit-Reset`.

## Consequences

- The key appears in no table and no log. The only copy `platform` ever sends is the creation answer, marked
  `Cache-Control: no-store`. A lost key cannot be recovered: the user revokes it and creates a new one.
- Every public call costs one lookup by index, one counter upsert and one `last_used_at` update. That is fine at
  1,000 calls per key per day. A much larger volume would call for a cache with a short lifetime, at the price of a
  delay before a revocation takes effect.
- A key does not expire. Revoking it is the user's job. An expiry date is an option for later.
- A key in a CI log or a public repository works until it is revoked. The `2bg_` prefix lets secret scanners (such
  as GitHub's) be told what to look for.
- Risk matrix entries to add: a leaked key (impact limited to one user's quota and read-only data), and the key
  shown in the browser once at creation.
