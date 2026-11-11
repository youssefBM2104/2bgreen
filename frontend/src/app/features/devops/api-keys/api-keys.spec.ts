import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ApiKeys } from './api-keys';
import { ApiKey } from './api-keys.service';

const KEYS = 'http://localhost:8080/api/v1/automation/api-keys';
const SECRET = '2bg_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_abcde';

describe('ApiKeys', () => {
  let fixture: ComponentFixture<ApiKeys>;
  let http: HttpTestingController;

  const root = () => fixture.nativeElement as HTMLElement;
  const text = () => root().textContent ?? '';
  const rows = () => [...root().querySelectorAll('.keys li')].map((li) => li.textContent ?? '');
  const input = () => root().querySelector('input') as HTMLInputElement;
  const button = (label: string) =>
    [...root().querySelectorAll('button')].find((b) => b.textContent?.includes(label));

  const key = (id: number, name: string, callsToday = 0): ApiKey => ({
    id,
    name,
    prefix: `2bg_k${id}xy`,
    createdAt: '2026-11-10T12:00:00Z',
    lastUsedAt: null,
    callsToday,
    dailyQuota: 1000,
  });

  function open(keys: ApiKey[]) {
    fixture = TestBed.createComponent(ApiKeys);
    fixture.detectChanges();
    http.expectOne({ method: 'GET', url: KEYS }).flush(keys);
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

  function click(label: string) {
    button(label)!.click();
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

  it('lists the keys with their prefix and the calls of the day', () => {
    open([key(1, 'CI', 12), { ...key(2, 'Cron'), lastUsedAt: '2026-11-10T13:30:00Z' }]);
    expect(rows()).toHaveLength(2);
    expect(rows()[0]).toContain('CI');
    expect(rows()[0]).toContain('2bg_k1xy…');
    expect(rows()[0]).toContain('12 / 1000 appels');
    expect(rows()[0]).toContain('dernier usage : jamais');
    // 13:30 UTC = 14:30 à Paris en novembre
    expect(rows()[1]).toContain('14:30');
    expect(text()).toContain('(2/5)');
  });

  it('tells when there is no key yet', () => {
    open([]);
    expect(text()).toContain("Vous n'avez pas encore de clé");
  });

  it('shows the new key once, then only its prefix', () => {
    open([]);
    type('  Pipeline CI ');
    submit();
    const request = http.expectOne({ method: 'POST', url: KEYS });
    expect(request.request.body).toEqual({ name: 'Pipeline CI' });
    request.flush({ ...key(3, 'Pipeline CI'), key: SECRET });
    fixture.detectChanges();

    expect(text()).toContain(SECRET);
    expect(text()).toContain('elle ne sera plus jamais affichée');
    expect(rows()[0]).toContain('2bg_k3xy…');
    expect(rows()[0]).not.toContain(SECRET);

    click("J'ai copié la clé");
    expect(text()).not.toContain(SECRET);
  });

  it('copies the new key', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    open([]);
    type('CI');
    submit();
    http.expectOne({ method: 'POST', url: KEYS }).flush({ ...key(1, 'CI'), key: SECRET });
    fixture.detectChanges();

    click('Copier la clé');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(writeText).toHaveBeenCalledWith(SECRET);
    expect(button('Copiée')).toBeTruthy();
  });

  it.each(['', '   ', 'x'.repeat(61)])('does not send the name "%s"', (value) => {
    open([]);
    type(value);
    submit();
    http.expectNone({ method: 'POST', url: KEYS });
  });

  it('stops offering creation at 5 keys', () => {
    open([1, 2, 3, 4, 5].map((id) => key(id, `k${id}`)));
    expect(root().querySelector('form')).toBeNull();
    expect(text()).toContain('limite de 5 clés actives');
  });

  it('explains a refusal of platform', () => {
    open([]);
    type('CI');
    submit();
    http
      .expectOne({ method: 'POST', url: KEYS })
      .flush({ code: 'API_KEY_LIMIT_REACHED' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(text()).toContain('Vous avez déjà 5 clés actives');
  });

  it('revokes a key after a confirmation', () => {
    open([key(1, 'CI'), key(2, 'Cron')]);
    click('Révoquer');
    http.expectNone({ method: 'DELETE' });
    click('Confirmer');
    http.expectOne({ method: 'DELETE', url: `${KEYS}/1` }).flush(null, { status: 204, statusText: '' });
    fixture.detectChanges();
    expect(rows()).toHaveLength(1);
    expect(rows()[0]).toContain('Cron');
  });

  it('keeps the key when the revocation is cancelled or fails', () => {
    open([key(1, 'CI')]);
    click('Révoquer');
    click('Annuler');
    http.expectNone({ method: 'DELETE' });

    click('Révoquer');
    click('Confirmer');
    http.expectOne({ method: 'DELETE', url: `${KEYS}/1` }).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(rows()).toHaveLength(1);
    expect(text()).toContain("n'a pas pu être révoquée");
  });

  it('says when the list cannot be read', () => {
    fixture = TestBed.createComponent(ApiKeys);
    fixture.detectChanges();
    http.expectOne(KEYS).flush({}, { status: 503, statusText: 'Error' });
    fixture.detectChanges();
    expect(text()).toContain('Impossible de lire vos clés');
  });
});
