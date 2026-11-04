/**
 * Read-only REST API consumed by {@code platform} (D8, contract K2, {@code docs/api/openapi.yaml}). No write
 * endpoints. Internal: no authentication, so it must stay on the Docker network and never be exposed publicly.
 * Controllers go through the use cases of {@code application}, never through {@code infrastructure.persistence}.
 */
package com.twobgreen.gridintelligence.infrastructure.api;
