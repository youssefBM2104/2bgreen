import { OpenIdConfiguration } from 'angular-auth-oidc-client';

export const PLATFORM_URL = 'http://localhost:8080';

// Client public (Authorization Code + PKCE) : aucun secret ici.
export const authConfig: OpenIdConfiguration = {
  authority: 'http://localhost:8180/realms/2bgreen',
  clientId: '2bgreen-frontend',
  // Avec le slash final : le client Keycloak n'accepte que `http://localhost:4200/*`.
  redirectUrl: `${window.location.origin}/`,
  postLogoutRedirectUri: `${window.location.origin}/`,
  responseType: 'code',
  scope: 'openid profile',
  silentRenew: true,
  useRefreshToken: true,
  renewTimeBeforeTokenExpiresInSeconds: 30,
  // Le token n'est joint qu'aux appels vers platform.
  secureRoutes: [`${PLATFORM_URL}/`],
  autoUserInfo: false,
};
