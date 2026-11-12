import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Targets } from './targets';
import { Target } from './targets.service';

const URL = 'http://localhost:8080/api/v1/regional/targets';

const NBSP = new RegExp('[\\u00a0\\u202f]', 'g');

const RENEWABLES: Target = {
  id: 7,
  indicator: 'RENEWABLE_SHARE',
  unit: '%',
  referenceYear: 2025,
  referenceValue: 40,
  referenceConsumption: null,
  targetValue: 60,
  deadline: '2030-12-31',
  tolerance: 2,
  createdAt: '2026-11-01T10:00:00Z',
  current: { day: '2026-11-11', value: 50, expected: 40.2, status: 'AHEAD', percent: 50, achieved: false },
  history: [
    { day: '2026-11-10', value: 49.5, expected: 40.1, status: 'AHEAD' },
    { day: '2026-11-11', value: 50, expected: 40.2, status: 'AHEAD' },
  ],
};

describe('Targets', () => {
  let fixture: ComponentFixture<Targets>;
  let http: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => (element().textContent ?? '').replace(NBSP, ' ').replace(/\s+/g, ' ');

  function start(targets: Target[]) {
    fixture = TestBed.createComponent(Targets);
    http.expectOne(URL).flush(targets);
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows each target with its value, the expected value, the way covered and its status', () => {
    start([RENEWABLES]);
    expect(text()).toContain('Part renouvelable');
    expect(text()).toContain('De 40 % (2025)');
    expect(text()).toContain('à 60 % le 31 décembre 2030');
    expect(text()).toContain('Valeur actuelle : 50 %');
    expect(text()).toContain("Attendue aujourd'hui : 40,2 %");
    expect(text()).toContain('Chemin parcouru : 50 %');
    expect(text()).toContain('En avance');
    expect(element().querySelector('[role=progressbar]')?.getAttribute('aria-valuenow')).toBe('50');
  });

  it('draws the progress chart with both series and a table of the same figures', () => {
    start([RENEWABLES]);
    expect(element().querySelectorAll('app-progress-chart polyline')).toHaveLength(2);
    expect(element().querySelectorAll('app-progress-chart circle.dot')).toHaveLength(2);
    expect(element().querySelectorAll('details tbody tr')).toHaveLength(2);
  });

  it('shows a reached target as achieved, at 100 % and no more', () => {
    start([
      {
        ...RENEWABLES,
        targetValue: 45,
        current: { ...RENEWABLES.current!, percent: 100, achieved: true },
      },
    ]);
    // The headline says achieved instead of a status; the history table still shows the status of each day
    expect(element().querySelector('.state')?.textContent?.trim()).toBe('Objectif atteint');
    expect(text()).toContain('Chemin parcouru : 100 %');
  });

  it('tells when a target has no value yet', () => {
    start([{ ...RENEWABLES, current: null, history: [] }]);
    expect(text()).toContain('Pas encore de valeur');
    expect(element().querySelector('app-progress-chart')).toBeNull();
  });

  it('shows the reference consumption of a consumption drop', () => {
    start([
      {
        ...RENEWABLES,
        indicator: 'CONSUMPTION_DROP',
        referenceValue: 0,
        referenceConsumption: 4559,
        targetValue: 10,
      },
    ]);
    expect(text()).toContain('Baisse de la consommation');
    expect(text()).toContain('consommation moyenne de 4 559 MW');
  });

  it('creates a target with what the form holds, then reads the list again', () => {
    start([]);
    const component = fixture.componentInstance as unknown as {
      form: { setValue: (v: object) => void };
      create: () => void;
    };
    component.form.setValue({
      indicator: 'ESTIMATED_CO2',
      referenceYear: 2025,
      targetValue: 150,
      deadline: '2030-12-31',
      tolerance: null,
    });
    component.create();
    const request = http.expectOne((r) => r.method === 'POST' && r.url === URL);
    expect(request.request.body).toEqual({
      indicator: 'ESTIMATED_CO2',
      referenceYear: 2025,
      targetValue: 150,
      deadline: '2030-12-31',
      tolerance: null,
    });
    request.flush({ ...RENEWABLES, indicator: 'ESTIMATED_CO2' });
    http.expectOne((r) => r.method === 'GET').flush([]);
  });

  it('explains a refused reference year', () => {
    start([]);
    const component = fixture.componentInstance as unknown as {
      form: { setValue: (v: object) => void };
      create: () => void;
    };
    component.form.setValue({
      indicator: 'RENEWABLE_SHARE',
      referenceYear: 2013,
      targetValue: 60,
      deadline: '2030-12-31',
      tolerance: null,
    });
    component.create();
    http
      .expectOne((r) => r.method === 'POST')
      .flush({ code: 'REFERENCE_UNAVAILABLE' }, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();
    expect(text()).toContain('Pas assez de données pour cette année de référence');
  });

  it('deletes a target and reads the list again', () => {
    start([RENEWABLES]);
    const deleteButton = [...element().querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Supprimer'),
    )!;
    deleteButton.click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === `${URL}/7`).flush(null);
    http.expectOne((r) => r.method === 'GET').flush([]);
    fixture.detectChanges();
    expect(text()).toContain("Vous n'avez pas encore d'objectif");
  });
});
