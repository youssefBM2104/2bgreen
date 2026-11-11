import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PLATFORM_URL } from '../../../core/auth/auth.config';

/** Une alerte « réseau propre » : prévenir quand le CO2 national passe sous `threshold`. */
export interface Alert {
  id: number;
  /** gCO2eq/kWh, de 1 à 1000. */
  threshold: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export const MIN_THRESHOLD = 1;
export const MAX_THRESHOLD = 1000;
export const MAX_ALERTS = 5;

const URL = `${PLATFORM_URL}/api/v1/automation/alerts`;

/** Les alertes de l'utilisateur connecté : platform lit son identité dans le token, jamais dans la requête. */
@Injectable({ providedIn: 'root' })
export class AlertsService {
  private readonly http = inject(HttpClient);

  list(): Observable<Alert[]> {
    return this.http.get<Alert[]>(URL);
  }

  create(threshold: number): Observable<Alert> {
    return this.http.post<Alert>(URL, { threshold });
  }

  replace(alert: Alert, changes: { threshold?: number; active?: boolean }): Observable<Alert> {
    return this.http.put<Alert>(`${URL}/${alert.id}`, {
      threshold: changes.threshold ?? alert.threshold,
      active: changes.active ?? alert.active,
    });
  }

  remove(alert: Alert): Observable<void> {
    return this.http.delete<void>(`${URL}/${alert.id}`);
  }
}
