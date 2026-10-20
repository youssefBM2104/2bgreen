import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';
import { CitizenHome } from './features/citizen/citizen-home';
import { DevopsHome } from './features/devops/devops-home';
import { OfficialHome } from './features/official/official-home';
import { NotFound } from './shared/not-found/not-found';

describe('routes', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    harness = await RouterTestingHarness.create();
  });

  it.each([
    ['/citoyen', CitizenHome],
    ['/devops', DevopsHome],
    ['/elu', OfficialHome],
  ])('%s displays its space', async (url, component) => {
    expect(await harness.navigateByUrl(url, component)).toBeInstanceOf(component);
  });

  it('redirects the root to /citoyen', async () => {
    await harness.navigateByUrl('/', CitizenHome);
    expect(TestBed.inject(Router).url).toBe('/citoyen');
  });

  it('displays the 404 page for an unknown URL', async () => {
    expect(await harness.navigateByUrl('/xyz', NotFound)).toBeInstanceOf(NotFound);
  });
});
