import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PLATFORM_URL } from '../../../core/auth/auth.config';

export type Balance = 'IMPORTING' | 'EXPORTING' | 'BALANCED';

/** MW par source. `thermal` regroupe tout le fossile : le régional ne distingue pas gaz, charbon et fioul. */
export interface Production {
  nuclear: number | null;
  thermal: number | null;
  wind: number | null;
  solar: number | null;
  hydro: number | null;
  bioenergy: number | null;
}

/** Une mesure et son bilan. Une valeur absente est `null`, jamais 0. */
export interface DashboardReading {
  observedAt: string;
  storedAt: string;
  quality: string;
  /** Mesure régionale seulement : plus vieille que `maxAge`. */
  stale: boolean | null;
  consumption: number | null;
  production: Production;
  totalProduction: number | null;
  /** Production / consommation, en %. */
  coverageRate: number | null;
  balance: Balance | null;
  /** Éolien, solaire, hydraulique et bioénergies dans la production, en %. */
  renewableShare: number | null;
  /** gCO2eq/kWh. */
  co2Intensity: number | null;
  /** Vrai quand `co2Intensity` est une estimation (toujours le cas pour une région). */
  co2Estimated: boolean;
}

export interface RegionalDashboard {
  region: { code: string; name: string };
  regional: DashboardReading;
  /** La France sur la même demi-heure que la mesure régionale, `null` sans donnée. */
  national: DashboardReading | null;
  /** Durée ISO-8601 au-delà de laquelle la mesure régionale est trop ancienne. */
  maxAge: string;
}

@Injectable({ providedIn: 'root' })
export class RegionalDashboardService {
  private readonly http = inject(HttpClient);

  /** La région vient du jeton : il n'y a rien à passer. */
  dashboard(): Observable<RegionalDashboard> {
    return this.http.get<RegionalDashboard>(`${PLATFORM_URL}/api/v1/regional/dashboard`);
  }
}
