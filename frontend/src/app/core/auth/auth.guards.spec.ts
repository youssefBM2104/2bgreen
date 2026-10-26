import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  provideRouter,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { homeGuard, roleGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { AppRole } from './roles';

describe('auth guards', () => {
  const login = vi.fn();

  function run(guard: CanActivateFn, user: { authenticated: boolean; roles?: AppRole[] }) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => of(user.authenticated),
            roles: () => of(user.roles ?? []),
            login,
          },
        },
      ],
    });
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return firstValueFrom(result as Observable<boolean | UrlTree>);
  }

  const url = (result: boolean | UrlTree) =>
    result instanceof UrlTree ? result.toString() : result;

  beforeEach(() => login.mockClear());

  describe('roleGuard', () => {
    it('sends an anonymous user to Keycloak and blocks the route', async () => {
      expect(await run(roleGuard('citizen'), { authenticated: false })).toBe(false);
      expect(login).toHaveBeenCalledOnce();
    });

    it.each<[AppRole]>([['citizen'], ['devops'], ['regional_official']])(
      'lets a %s in',
      async (role) => {
        expect(await run(roleGuard(role), { authenticated: true, roles: [role] })).toBe(true);
        expect(login).not.toHaveBeenCalled();
      },
    );

    it('lets in a user who has several roles, including the required one', async () => {
      const result = await run(roleGuard('devops'), {
        authenticated: true,
        roles: ['citizen', 'devops'],
      });
      expect(result).toBe(true);
    });

    it.each<[AppRole, AppRole]>([
      ['devops', 'citizen'],
      ['regional_official', 'citizen'],
      ['citizen', 'devops'],
      ['regional_official', 'devops'],
      ['citizen', 'regional_official'],
      ['devops', 'regional_official'],
    ])('sends a %s who asks for the %s space to "access denied"', async (userRole, required) => {
      const result = await run(roleGuard(required), { authenticated: true, roles: [userRole] });
      expect(url(result)).toBe('/acces-refuse');
      expect(login).not.toHaveBeenCalled();
    });

    it('sends a connected user without any 2bGreen role to "access denied"', async () => {
      const result = await run(roleGuard('citizen'), { authenticated: true, roles: [] });
      expect(url(result)).toBe('/acces-refuse');
    });
  });

  describe('homeGuard', () => {
    it('sends an anonymous user to Keycloak', async () => {
      expect(await run(homeGuard, { authenticated: false })).toBe(false);
      expect(login).toHaveBeenCalledOnce();
    });

    it.each<[AppRole, string]>([
      ['citizen', '/citoyen'],
      ['devops', '/devops'],
      ['regional_official', '/elu'],
    ])('sends a %s to %s', async (role, path) => {
      expect(url(await run(homeGuard, { authenticated: true, roles: [role] }))).toBe(path);
    });

    it('picks the citizen space first when the user has several roles', async () => {
      const result = await run(homeGuard, { authenticated: true, roles: ['devops', 'citizen'] });
      expect(url(result)).toBe('/citoyen');
    });

    it('sends a user without any 2bGreen role to "access denied"', async () => {
      expect(url(await run(homeGuard, { authenticated: true, roles: [] }))).toBe('/acces-refuse');
    });
  });
});
