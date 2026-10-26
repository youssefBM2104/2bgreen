import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import {
  authInterceptor,
  provideAuth,
  withAppInitializerAuthCheck,
} from 'angular-auth-oidc-client';

import { routes } from './app.routes';
import { authConfig } from './core/auth/auth.config';
import { AuthService } from './core/auth/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([authInterceptor()])),
    // Traite le retour de Keycloak (?code=...) avant que le routeur et les guards ne démarrent.
    provideAuth({ config: authConfig }, withAppInitializerAuthCheck()),
    provideAppInitializer(() => inject(AuthService).watchSessionExpiry()),
    provideRouter(routes),
  ],
};
