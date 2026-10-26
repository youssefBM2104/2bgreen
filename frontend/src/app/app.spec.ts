import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            roles: () => of(['citizen', 'devops', 'regional_official']),
            isLoggedIn: signal(true),
            logout: vi.fn(),
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show the app name and a link to each of the 3 spaces', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.brand')?.textContent).toContain('2bGreen');
    const hrefs = Array.from(root.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/citoyen', '/devops', '/elu']);
  });

  it('shows only the space of the user role and a logout button', async () => {
    TestBed.overrideProvider(AuthService, {
      useValue: { roles: () => of(['devops']), isLoggedIn: signal(true), logout: vi.fn() },
    });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const hrefs = Array.from(root.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(hrefs).toEqual(['/devops']);
    expect(root.querySelector('.logout')?.textContent).toContain('Se déconnecter');
  });
});
