import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { REFRESH_MS, TrafficLight } from './traffic-light';
import { TrafficLightLevel } from './traffic-light.service';

const URL = 'http://localhost:8080/api/v1/advisory/traffic-light';

describe('TrafficLight', () => {
  let fixture: ComponentFixture<TrafficLight>;
  let http: HttpTestingController;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const lit = () =>
    [...(fixture.nativeElement as HTMLElement).querySelectorAll('.lamp.on')].map((lamp) =>
      lamp.className.replace(/lamp|on|\s/g, ''),
    );

  const reading = (level: TrafficLightLevel, overrides = {}) => ({
    level,
    co2Intensity: 29,
    observedAt: '2026-11-04T14:45:00Z',
    stale: false,
    ...overrides,
  });

  async function start() {
    fixture = TestBed.createComponent(TrafficLight);
    await vi.advanceTimersByTimeAsync(0);
  }

  function answer(body: object, options?: { status: number; statusText: string }) {
    http.expectOne(URL).flush(body, options);
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.useRealTimers();
  });

  it('shows a loading message before the answer', async () => {
    await start();
    fixture.detectChanges();
    expect(text()).toContain('Chargement');
    http.expectOne(URL);
  });

  it.each<[TrafficLightLevel, string, string]>([
    ['GREEN', 'green', 'Réseau propre'],
    ['AMBER', 'amber', 'Réseau moyen'],
    ['RED', 'red', 'Réseau carboné'],
  ])('lights only the %s lamp', async (level, lamp, title) => {
    await start();
    answer(reading(level));
    expect(lit()).toEqual([lamp]);
    expect(text()).toContain(title);
  });

  it('shows the value and the time of the data in Paris time', async () => {
    await start();
    answer(reading('GREEN'));
    expect(text()).toContain('29 gCO₂éq/kWh');
    expect(text()).toContain('04/11/2026 15:45');
  });

  it('does not present a stale reading as current', async () => {
    await start();
    answer(reading('UNKNOWN', { stale: true }));
    expect(lit()).toEqual([]);
    expect(text()).toContain('Données trop anciennes');
    expect(text()).toContain('Dernière valeur connue : 29 gCO₂éq/kWh');
    expect(text()).not.toContain('Bon moment');
  });

  it('lights nothing and shows no number when the CO2 value is missing', async () => {
    await start();
    answer(reading('UNKNOWN', { co2Intensity: null }));
    expect(lit()).toEqual([]);
    expect(text()).toContain('Niveau inconnu');
    expect(text()).not.toContain('gCO₂éq/kWh,');
  });

  it('keeps a real zero as a value', async () => {
    await start();
    answer(reading('GREEN', { co2Intensity: 0 }));
    expect(text()).toContain('0 gCO₂éq/kWh');
  });

  it('tells when there is no data yet', async () => {
    await start();
    answer({ code: 'NO_DATA' }, { status: 404, statusText: 'Not Found' });
    expect(text()).toContain('Pas encore de données');
    expect(lit()).toEqual([]);
  });

  it.each([
    [503, { code: 'GRID_UNAVAILABLE' }],
    [404, {}],
    [401, {}],
  ])('tells the data is unavailable on a %i without a known code', async (status, body) => {
    await start();
    answer(body, { status, statusText: 'Error' });
    expect(text()).toContain('Données indisponibles');
    expect(lit()).toEqual([]);
  });

  it('refreshes by itself, so an open page does not keep an old colour', async () => {
    await start();
    answer(reading('GREEN'));
    await vi.advanceTimersByTimeAsync(REFRESH_MS);
    answer(reading('UNKNOWN', { stale: true }));
    expect(lit()).toEqual([]);
    expect(text()).toContain('Données trop anciennes');
  });

  it('recovers after a failure', async () => {
    await start();
    answer({}, { status: 503, statusText: 'Error' });
    await vi.advanceTimersByTimeAsync(REFRESH_MS);
    answer(reading('RED'));
    expect(lit()).toEqual(['red']);
  });
});
