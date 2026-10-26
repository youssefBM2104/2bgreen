import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { AppRole } from './core/auth/roles';
import { AccessDenied } from './shared/access-denied/access-denied';
import { CitizenHome } from './features/citizen/citizen-home';
import { DevopsHome } from './features/devops/devops-home';
import { OfficialHome } from './features/official/official-home';
import { NotFound } from './shared/not-found/not-found';

describe('routes', () => {
  let harness: RouterTestingHarness;

  async function setup(roles: AppRole[]) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        {
          provide: AuthService,
          useValue: { isAuthenticated: () => of(true), roles: () => of(roles), login: vi.fn() },
        },
      ],
    });
    harness = await RouterTestingHarness.create();
  }

  it.each([
    ['/citoyen', 'citizen', CitizenHome],
    ['/devops', 'devops', DevopsHome],
    ['/elu', 'regional_official', OfficialHome],
  ] as const)('%s displays its space to a %s', async (url, role, component) => {
    await setup([role]);
    expect(await harness.navigateByUrl(url, component)).toBeInstanceOf(component);
  });

  it('refuses a citizen who asks for the devops space', async () => {
    await setup(['citizen']);
    await harness.navigateByUrl('/devops', AccessDenied);
    expect(TestBed.inject(Router).url).toBe('/acces-refuse');
  });

  it.each([
    ['citizen', '/citoyen', CitizenHome],
    ['devops', '/devops', DevopsHome],
    ['regional_official', '/elu', OfficialHome],
  ] as const)('redirects the root to the space of a %s', async (role, path, component) => {
    await setup([role]);
    await harness.navigateByUrl('/', component);
    expect(TestBed.inject(Router).url).toBe(path);
  });

  it('displays the 404 page for an unknown URL', async () => {
    await setup(['citizen']);
    expect(await harness.navigateByUrl('/xyz', NotFound)).toBeInstanceOf(NotFound);
  });
});
