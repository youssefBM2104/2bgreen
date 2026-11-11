import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Alerts } from './alerts';
import { Alert } from './alerts.service';

const ALERTS = 'http://localhost:8080/api/v1/automation/alerts';
const LIMITS = 'http://localhost:8080/api/v1/advisory/traffic-light/limits';

describe('Alerts', () => {
  let fixture: ComponentFixture<Alerts>;
  let http: HttpTestingController;

  const root = () => fixture.nativeElement as HTMLElement;
  const text = () => root().textContent ?? '';
  const rows = () => [...root().querySelectorAll('.alerts li')].map((li) => li.textContent ?? '');
  const input = () => root().querySelector('input') as HTMLInputElement;
  const button = (label: string) =>
    [...root().querySelectorAll('button')].find((b) => b.textContent?.includes(label));

  const alert = (id: number, threshold: number, active = true): Alert => ({
    id,
    threshold,
    active,
    createdAt: '2026-11-10T12:00:00Z',
    updatedAt: '2026-11-10T12:00:00Z',
  });

  /** Ouvre la page et répond aux deux lectures du départ. */
  function open(alerts: Alert[], limits: object | null = { greenBelow: 20, amberBelow: 40 }) {
    fixture = TestBed.createComponent(Alerts);
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: ALERTS }).flush(alerts);
    const limitsRequest = http.expectOne(LIMITS);
    if (limits) {
      limitsRequest.flush(limits);
    } else {
      limitsRequest.flush({}, { status: 503, statusText: 'Error' });
    }
    fixture.detectChanges();
  }

  function type(value: string) {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function submit() {
    root().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('lists the alerts of the user with their state', () => {
    open([alert(1, 30), alert(2, 15, false)]);
    expect(rows()).toHaveLength(2);
    expect(rows()[0]).toContain('Sous 30 gCO₂éq/kWh');
    expect(rows()[0]).toContain('Active');
    expect(rows()[1]).toContain('Sous 15 gCO₂éq/kWh');
    expect(rows()[1]).toContain('En pause');
    expect(text()).toContain('(2/5)');
  });

  it('tells when there is no alert yet', () => {
    open([]);
    expect(text()).toContain("Vous n'avez pas encore d'alerte");
  });

  it('proposes the limits of the traffic light, the green one by default', () => {
    open([]);
    expect(input().value).toBe('20');
    expect(text()).toContain('Feu vert : sous 20');
    button('Feu orange')!.click();
    fixture.detectChanges();
    expect(input().value).toBe('40');
  });

  it('still works when the limits cannot be read', () => {
    open([], null);
    expect(text()).not.toContain('Feu vert');
    type('35');
    submit();
    http.expectOne({ method: 'POST', url: ALERTS }).flush(alert(1, 35));
    fixture.detectChanges();
    expect(rows()).toHaveLength(1);
  });

  it('creates an alert and adds it to the list', () => {
    open([alert(1, 30)]);
    type('25');
    submit();
    const request = http.expectOne({ method: 'POST', url: ALERTS });
    expect(request.request.body).toEqual({ threshold: 25 });
    request.flush(alert(2, 25));
    fixture.detectChanges();
    expect(rows()).toHaveLength(2);
    expect(rows()[1]).toContain('Sous 25 gCO₂éq/kWh');
  });

  it.each(['0', '1001', '', '12.5', '-3'])('does not send the threshold "%s"', (value) => {
    open([]);
    type(value);
    submit();
    http.expectNone({ method: 'POST', url: ALERTS });
    expect(text()).toContain('Un nombre entier entre 1 et 1000');
  });

  it('replaces the form by a message once the user has 5 alerts', () => {
    open([1, 2, 3, 4, 5].map((id) => alert(id, id * 10)));
    expect(root().querySelector('form')).toBeNull();
    expect(text()).toContain('limite de 5 alertes');
  });

  it('shows the refusal of the platform when the limit is reached elsewhere', () => {
    open([alert(1, 30)]);
    submit();
    http
      .expectOne({ method: 'POST', url: ALERTS })
      .flush({ code: 'ALERT_LIMIT_REACHED' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(root().querySelector('[role=alert]')?.textContent).toContain('Vous avez déjà 5 alertes');
    expect(rows()).toHaveLength(1);
  });

  it('pauses an alert, keeping its threshold', () => {
    open([alert(1, 30)]);
    (root().querySelector('button[role=switch]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const request = http.expectOne({ method: 'PUT', url: `${ALERTS}/1` });
    expect(request.request.body).toEqual({ threshold: 30, active: false });
    request.flush(alert(1, 30, false));
    fixture.detectChanges();
    expect(rows()[0]).toContain('En pause');
  });

  it('puts the switch back when the change is refused', () => {
    open([alert(1, 30)]);
    (root().querySelector('button[role=switch]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(rows()[0]).toContain('En pause');
    http
      .expectOne({ method: 'PUT', url: `${ALERTS}/1` })
      .flush({}, { status: 503, statusText: 'Error' });
    fixture.detectChanges();
    expect(rows()[0]).toContain('Active');
    expect(root().querySelector('[role=alert]')?.textContent).toContain(
      "n'a pas pu être enregistrée",
    );
  });

  it('deletes an alert', () => {
    open([alert(1, 30), alert(2, 15)]);
    (root().querySelector('.remove') as HTMLButtonElement).click();
    http.expectOne({ method: 'DELETE', url: `${ALERTS}/1` }).flush(null);
    fixture.detectChanges();
    expect(rows()).toHaveLength(1);
    expect(rows()[0]).toContain('Sous 15');
  });

  it('tells when the alerts cannot be read', () => {
    fixture = TestBed.createComponent(Alerts);
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: ALERTS }).flush({}, { status: 503, statusText: 'Error' });
    http.expectOne(LIMITS).flush({ greenBelow: 20, amberBelow: 40 });
    fixture.detectChanges();
    expect(root().querySelector('[role=alert]')?.textContent).toContain(
      'Impossible de lire vos alertes',
    );
    expect(text()).not.toContain('Chargement');
  });
});
