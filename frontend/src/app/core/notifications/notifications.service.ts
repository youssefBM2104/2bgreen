import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PLATFORM_URL } from '../auth/auth.config';

export interface AppNotification {
  id: number;
  type: 'CLEAN_GRID' | 'TARGET_PACE';
  /** Écrit par platform : l'heure de la mesure, l'intensité et le seuil franchi. */
  message: string;
  createdAt: string;
  /** `null` tant que la notification n'est pas lue. */
  readAt: string | null;
}

export interface NotificationBox {
  /** Toutes les non lues, même celles trop anciennes pour être dans `items`. */
  unreadCount: number;
  /** Les plus récentes d'abord. */
  items: AppNotification[];
}

const URL = `${PLATFORM_URL}/api/v1/notifications`;

/** La boîte de notifications de l'utilisateur connecté (citoyen, élu régional). */
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);

  box(): Observable<NotificationBox> {
    return this.http.get<NotificationBox>(URL);
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${URL}/${id}/read`, null);
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${URL}/read-all`, null);
  }
}
