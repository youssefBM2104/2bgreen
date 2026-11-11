package com.twobgreen.platform.automation.infrastructure;

/**
 * Who calls {@code /api/v1/public/**}: the principal set by {@link ApiKeyAuthenticationFilter}, read by the public
 * controllers with {@code @AuthenticationPrincipal}. It holds the id of the key, never the key itself.
 *
 * @param userId the {@code sub} of the Keycloak user who owns the key
 */
public record ApiKeyCaller(long keyId, String userId) {
}
