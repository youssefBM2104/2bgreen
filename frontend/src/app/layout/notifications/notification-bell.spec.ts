import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppNotification } from '../../core/notifications/notifications.service';
import { NotificationBell, POLL_MS } from './notification-bell';

const BOX = 'http://localhost:8080/api/v1/notifications';

describe('NotificationBell', () => {
  let fixture: ComponentFixture<NotificationBell>;
  let http: HttpTestingController;

  const bell = () => (fixture.nativeElement as HTMLElement).querySelector('button')!;
  const badge = () => (fixture.nativeElement as HTMLElement).querySelector('.mat-badge-content');
  const badgeHidden = () =>
    (fixture.nativeElement as HTMLElement).querySelector('.mat-badge-hidden') !== null;
  // Le menu Material est rendu dans un overlay, hors du composant.
  const panel = () => document.querySelector('.notification-panel') as HTMLElement | null;
  const items = () =>
    [...(panel()?.querySelectorAll('.notification') ?? [])] as HTMLButtonElement[];

  const notification = (id: number, overrides: Partial<AppNotification> = {}): AppNotification => ({
    id,
    type: 'CLEAN_GRID',
    message: `Réseau propre : ${id} gCO₂eq/kWh le 10/11 à 13:30, sous votre seuil de 30.`,
    createdAt: '2026-11-10T12:47:00Z',
    readAt: null,
    ...overrides,
  });

  async function start() {
    fixture = TestBed.createComponent(NotificationBell);
    await vi.advanceTimersByTimeAsync(0);
  }

  function answer(items: AppNotification[], unreadCount = items.filter((i) => !i.readAt).length) {
    http.expectOne({ method: 'GET', url: BOX }).flush({ unreadCount, items });
    fixture.detectChanges();
  }

  function openBox() {
    bell().click();
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

  it('shows the number of unread notifications on the badge', async () => {
    await start();
    answer([notification(1), notification(2), notification(3, { readAt: '2026-11-10T13:00:00Z' })]);
    expect(badge()?.textContent).toContain('2');
    expect(badgeHidden()).toBe(false);
    expect(bell().getAttribute('aria-label')).toBe('Notifications, 2 non lues');
  });

  it('hides the badge when everything is read', async () => {
    await start();
    answer([notification(1, { readAt: '2026-11-10T13:00:00Z' })]);
    expect(badgeHidden()).toBe(true);
    expect(bell().getAttribute('aria-label')).toBe('Notifications');
  });

  it('asks again every minute, so a new notification appears by itself', async () => {
    await start();
    answer([]);
    expect(badgeHidden()).toBe(true);
    await vi.advanceTimersByTimeAsync(POLL_MS);
    answer([notification(1)]);
    expect(badge()?.textContent).toContain('1');
  });

  it('keeps what it knows when a poll fails, and recovers at the next one', async () => {
    await start();
    answer([notification(1)]);
    await vi.advanceTimersByTimeAsync(POLL_MS);
    http.expectOne(BOX).flush({}, { status: 503, statusText: 'Error' });
    fixture.detectChanges();
    expect(badge()?.textContent).toContain('1');
    await vi.advanceTimersByTimeAsync(POLL_MS);
    answer([notification(1), notification(2)]);
    expect(badge()?.textContent).toContain('2');
  });

  it('lists the notifications with their message and the time received in Paris time', async () => {
    await start();
    answer([notification(29), notification(28)]);
    openBox();
    expect(items()).toHaveLength(2);
    expect(items()[0].textContent).toContain('Réseau propre : 29 gCO₂eq/kWh le 10/11 à 13:30');
    expect(items()[0].textContent).toContain('sous votre seuil de 30');
    expect(items()[0].textContent).toContain('Reçue le 10/11/2026 13:47');
    expect(items()[0].classList).toContain('unread');
  });

  it('tells when the box is empty', async () => {
    await start();
    answer([]);
    openBox();
    expect(panel()?.textContent).toContain('Aucune notification');
  });

  it('marks a notification as read on click, then reads the box again', async () => {
    await start();
    answer([notification(1), notification(2)]);
    openBox();
    items()[0].click();
    http.expectOne({ method: 'POST', url: `${BOX}/1/read` }).flush(null);
    answer([notification(1, { readAt: '2026-11-10T13:00:00Z' }), notification(2)]);
    expect(badge()?.textContent).toContain('1');
    expect(items()[0].classList).not.toContain('unread');
  });

  it('sends nothing for a notification already read', async () => {
    await start();
    answer([notification(1, { readAt: '2026-11-10T13:00:00Z' })]);
    openBox();
    items()[0].click();
    http.expectNone(`${BOX}/1/read`);
  });

  it('marks everything as read at once', async () => {
    await start();
    answer([notification(1), notification(2)]);
    openBox();
    (panel()!.querySelector('.read-all') as HTMLButtonElement).click();
    http.expectOne({ method: 'POST', url: `${BOX}/read-all` }).flush(null);
    answer([
      notification(1, { readAt: '2026-11-10T13:00:00Z' }),
      notification(2, { readAt: '2026-11-10T13:00:00Z' }),
    ]);
    expect(badgeHidden()).toBe(true);
  });
});
