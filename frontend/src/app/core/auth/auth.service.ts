import { computed, inject, Injectable } from '@angular/core';
import { EventTypes, OidcSecurityService, PublicEventsService } from 'angular-auth-oidc-client';
import { catchError, filter, map, Observable, of } from 'rxjs';
import { AppRole, rolesFromPayload } from './roles';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);
  private readonly events = inject(PublicEventsService);

  readonly isLoggedIn = computed(() => this.oidc.authenticated().isAuthenticated);

  isAuthenticated(): Observable<boolean> {
    return this.oidc.isAuthenticated();
  }

  /** Rôles 2bGreen lus dans l'access token (c'est là que Keycloak met `realm_access`). */
  roles(): Observable<AppRole[]> {
    return this.oidc.getPayloadFromAccessToken().pipe(
      map(rolesFromPayload),
      catchError(() => of([])),
    );
  }

  login(): void {
    this.oidc.authorize();
  }

  logout(): void {
    this.oidc.logoff().subscribe();
  }

  /** Si le renouvellement du token échoue (session Keycloak expirée), on renvoie vers le login. */
  watchSessionExpiry(): void {
    this.events
      .registerForEvents()
      .pipe(filter((event) => event.type === EventTypes.SilentRenewFailed))
      .subscribe(() => this.login());
  }
}
