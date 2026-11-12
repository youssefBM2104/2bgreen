import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CO2_METHOD_URL, REFRESH_MS, RegionalDashboard } from './regional-dashboard';
import { DashboardReading } from './regional-dashboard.service';

const URL = 'http://localhost:8080/api/v1/regional/dashboard';

/** Provence-Alpes-Côte d'Azur, eco2mix-regional-tr du 2026-11-10 12:00 UTC, CO₂ estimé comme le fait G7. */
const PACA: DashboardReading = {
  observedAt: '2026-11-10T12:00:00Z',
  storedAt: '2026-11-10T13:05:00Z',
  quality: 'PROVISIONAL',
  stale: false,
  consumption: 4559,
  production: { nuclear: 0, thermal: 1417, wind: 25, solar: 556, hydro: 1606, bioenergy: 138 },
  totalProduction: 3742,
  coverageRate: 82.1,
  balance: 'IMPORTING',
  renewableShare: 62.1,
  co2Intensity: 181,
  co2Estimated: true,
};

const FRANCE: DashboardReading = {
  ...PACA,
  stale: null,
  consumption: 59437,
  production: { nuclear: 41561, thermal: 7351, wind: 3355, solar: 7919, hydro: 6240, bioenergy: 959 },
  totalProduction: 67385,
  coverageRate: 113.4,
  balance: 'EXPORTING',
  renewableShare: 27.5,
  co2Intensity: 59,
  co2Estimated: false,
};

describe('RegionalDashboard', () => {
  let fixture: ComponentFixture<RegionalDashboard>;
  let http: HttpTestingController;

  // Les nombres français séparent les milliers par une espace insécable fine
  const NBSP = new RegExp('[\\u00a0\\u202f]', 'g');
  const text = () => ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(NBSP, ' ');
  const cells = (row: string) => {
    const tr = [...(fixture.nativeElement as HTMLElement).querySelectorAll('tr')].find(
      (line) => line.querySelector('th')?.textContent?.trim() === row,
    );
    return [...(tr?.querySelectorAll('td') ?? [])].map((td) =>
      (td.textContent ?? '').replace(NBSP, ' ').replace(/\s+/g, ' ').trim(),
    );
  };

  const body = (overrides: { regional?: Partial<DashboardReading>; national?: unknown } = {}) => ({
    region: { code: '93', name: "Provence-Alpes-Côte d'Azur" },
    regional: { ...PACA, ...overrides.regional },
    national: 'national' in overrides ? overrides.national : FRANCE,
    maxAge: 'PT4H',
  });

  async function start() {
    fixture = TestBed.createComponent(RegionalDashboard);
    await vi.advanceTimersByTimeAsync(0);
  }

  function answer(response: object, options?: { status: number; statusText: string }) {
    http.expectOne(URL).flush(response, options);
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

  it('asks for the dashboard without naming a region: it comes from the token', async () => {
    await start();
    fixture.detectChanges();
    expect(text()).toContain('Chargement');
    const request = http.expectOne(URL);
    expect(request.request.params.keys()).toEqual([]);
  });

  it('shows the region, its consumption and its production by source', async () => {
    await start();
    answer(body());
    expect(text()).toContain("Provence-Alpes-Côte d'Azur");
    expect(cells('Consommation')).toEqual(['4 559 MW', '59 437 MW']);
    expect(cells('Production')).toEqual(['3 742 MW', '67 385 MW']);
    expect(cells('Thermique fossile (gaz, charbon, fioul)')).toEqual(['1 417 MW', '7 351 MW']);
    expect(cells('Hydraulique')).toEqual(['1 606 MW', '6 240 MW']);
    expect(cells('Part renouvelable')).toEqual(['62,1 %', '27,5 %']);
  });

  it('says whether the region imports or exports, with its coverage rate', async () => {
    await start();
    answer(body());
    expect(text()).toContain('La région importe');
    expect(text()).toContain('82,1 % de sa consommation');
    expect(cells('Taux de couverture')).toEqual(['82,1 %', '113,4 %']);
  });

  it('marks the regional CO2 as estimated and links to the method', async () => {
    await start();
    answer(body());
    expect(cells('Intensité CO₂')).toEqual(['181 gCO₂éq/kWh estimée', '59 gCO₂éq/kWh valeur RTE']);
    const link = (fixture.nativeElement as HTMLElement).querySelector(
      `a[href="${CO2_METHOD_URL}"]`,
    );
    expect(link).not.toBeNull();
  });

  it('shows no CO2 value and no estimated badge when the estimate is missing', async () => {
    await start();
    answer(body({ regional: { co2Intensity: null, co2Estimated: false } }));
    expect(cells('Intensité CO₂')[0]).toBe('—');
  });

  it('shows the time of the data in Paris time and says the lag is normal', async () => {
    await start();
    answer(body());
    expect(text()).toContain('Mesure du 10/11/2026 13:00 (heure de Paris)');
    expect(text()).toContain('quelques heures de retard');
  });

  it('warns when the regional reading is older than the limit, and still shows it', async () => {
    await start();
    answer(body({ regional: { stale: true } }));
    expect(text()).toContain('Données trop anciennes');
    expect(text()).toContain('plus de 4 h');
    expect(cells('Consommation')[0]).toBe('4 559 MW');
  });

  it('shows missing values as missing, never as 0', async () => {
    await start();
    answer(
      body({
        regional: {
          production: { ...PACA.production, hydro: null },
          totalProduction: null,
          coverageRate: null,
          balance: null,
          renewableShare: null,
        },
      }),
    );
    expect(cells('Hydraulique')[0]).toBe('—');
    expect(cells('Production')[0]).toBe('—');
    expect(cells('Taux de couverture')[0]).toBe('—');
    expect(text()).not.toContain('La région importe');
    // A real zero stays a value
    expect(cells('Nucléaire')[0]).toBe('0 MW');
  });

  it('still shows the region without national data for that hour', async () => {
    await start();
    answer(body({ national: null }));
    expect(cells('Consommation')).toEqual(['4 559 MW', '—']);
    expect(text()).toContain('Pas de donnée nationale pour cette heure-là');
  });

  it('tells an official without region to ask an administrator', async () => {
    await start();
    answer({ code: 'REGION_NOT_ASSIGNED' }, { status: 403, statusText: 'Forbidden' });
    expect(text()).toContain('Aucune région rattachée');
  });

  it('tells when the region has no data yet', async () => {
    await start();
    answer({ code: 'NO_DATA' }, { status: 404, statusText: 'Not Found' });
    expect(text()).toContain('Pas encore de données');
  });

  it.each([
    [503, { code: 'GRID_UNAVAILABLE' }],
    [403, {}],
    [404, {}],
  ])('tells the data is unavailable on a %i without a known code', async (status, response) => {
    await start();
    answer(response, { status, statusText: 'Error' });
    expect(text()).toContain('Données indisponibles');
  });

  it('refreshes by itself', async () => {
    await start();
    answer({}, { status: 503, statusText: 'Error' });
    await vi.advanceTimersByTimeAsync(REFRESH_MS);
    answer(body());
    expect(text()).toContain('La région importe');
  });
});
