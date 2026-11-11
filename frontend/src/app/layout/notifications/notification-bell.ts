import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { catchError, merge, of, Subject, switchMap, timer } from 'rxjs';
import {
  AppNotification,
  NotificationBox,
  NotificationsService,
} from '../../core/notifications/notifications.service';

/** Une fois par minute suffit : les mesures du réseau arrivent toutes les 15 minutes. */
export const POLL_MS = 60 * 1000;

// France uniquement : l'heure affichée est celle de Paris, quel que soit le fuseau de la machine.
const PARIS_TIME = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris',
  dateStyle: 'short',
  timeStyle: 'short',
});

/** La cloche de l'en-tête (U2) : pastille des notifications non lues et boîte de notifications. */
@Component({
  selector: 'app-notification-bell',
  imports: [MatBadgeModule, MatButtonModule, MatIconModule, MatMenuModule],
  templateUrl: './notification-bell.html',
  styleUrl: './notification-bell.scss',
})
export class NotificationBell {
  private readonly service = inject(NotificationsService);

  private readonly reload = new Subject<void>();

  /** `null` tant qu'aucune réponse n'est arrivée. */
  protected readonly box = signal<NotificationBox | null>(null);
  protected readonly unread = computed(() => this.box()?.unreadCount ?? 0);
  protected readonly label = computed(() => {
    const unread = this.unread();
    if (unread === 0) {
      return 'Notifications';
    }
    return `Notifications, ${unread} non ${unread === 1 ? 'lue' : 'lues'}`;
  });

  constructor() {
    merge(timer(0, POLL_MS), this.reload)
      .pipe(
        // Une interrogation qui échoue ne vide pas la boîte : la suivante réessaie.
        switchMap(() => this.service.box().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((box) => {
        if (box) {
          this.box.set(box);
        }
      });
  }

  protected time(notification: AppNotification): string {
    return PARIS_TIME.format(new Date(notification.createdAt));
  }

  protected read(notification: AppNotification): void {
    if (notification.readAt === null) {
      this.service.markRead(notification.id).subscribe({
        next: () => this.reload.next(),
        error: () => this.reload.next(),
      });
    }
  }

  protected readAll(): void {
    this.service.markAllRead().subscribe({
      next: () => this.reload.next(),
      error: () => this.reload.next(),
    });
  }
}
