import { inject } from '@angular/core';
import { CanActivateFn, GuardResult, Router } from '@angular/router';
import { map, of, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { APP_ROLES, AppRole, ROLE_HOME } from './roles';

const ACCESS_DENIED = '/acces-refuse';

/** Non connecté : redirection vers Keycloak. Connecté : `decide` choisit entre laisser passer ou rediriger. */
function authGuard(decide: (roles: AppRole[], router: Router) => GuardResult): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.isAuthenticated().pipe(
      switchMap((authenticated) => {
        if (!authenticated) {
          auth.login();
          return of(false);
        }
        return auth.roles().pipe(map((roles) => decide(roles, router)));
      }),
    );
  };
}

/** Réservé aux utilisateurs qui ont `role` ; les autres vont sur la page « accès refusé ». */
export const roleGuard = (role: AppRole): CanActivateFn =>
  authGuard((roles, router) => roles.includes(role) || router.parseUrl(ACCESS_DENIED));

/** Racine `/` : envoie chacun vers son espace (ou « accès refusé » s'il n'a aucun rôle 2bGreen). */
export const homeGuard: CanActivateFn = authGuard((roles, router) => {
  const role = APP_ROLES.find((r) => roles.includes(r));
  return router.parseUrl(role ? ROLE_HOME[role] : ACCESS_DENIED);
});
