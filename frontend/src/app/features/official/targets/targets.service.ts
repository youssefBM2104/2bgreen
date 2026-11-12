import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PLATFORM_URL } from '../../../core/auth/auth.config';

export type Indicator = 'RENEWABLE_SHARE' | 'COVERAGE_RATE' | 'CONSUMPTION_DROP' | 'ESTIMATED_CO2';

export type TargetStatus = 'AHEAD' | 'ON_TRACK' | 'LATE';

/** Une journée de l'historique : la valeur sur les 12 mois précédents et la trajectoire ce jour-là. */
export interface TargetPoint {
  day: string;
  value: number;
  expected: number;
  status: TargetStatus;
}

export interface Target {
  id: number;
  indicator: Indicator;
  /** `%` ou `gCO2eq/kWh`. */
  unit: string;
  referenceYear: number;
  /** Départ de la trajectoire, dans `unit` (0 pour une baisse de consommation). */
  referenceValue: number;
  /** Consommation moyenne de l'année de référence, MW (baisse de consommation seulement). */
  referenceConsumption: number | null;
  targetValue: number;
  deadline: string;
  tolerance: number | null;
  createdAt: string;
  /** `null` tant qu'aucune valeur n'a pu être calculée. */
  current: (TargetPoint & { percent: number; achieved: boolean }) | null;
  history: TargetPoint[];
}

export interface NewTarget {
  indicator: Indicator;
  referenceYear: number;
  targetValue: number;
  deadline: string;
  tolerance: number | null;
}

export type TargetChanges = Pick<NewTarget, 'targetValue' | 'deadline' | 'tolerance'>;

export const MAX_TARGETS = 10;

const URL = `${PLATFORM_URL}/api/v1/regional/targets`;

/** Les objectifs de l'élu (U13). La région vient du jeton : rien à passer. */
@Injectable({ providedIn: 'root' })
export class TargetsService {
  private readonly http = inject(HttpClient);

  list(): Observable<Target[]> {
    return this.http.get<Target[]>(URL);
  }

  create(target: NewTarget): Observable<Target> {
    return this.http.post<Target>(URL, target);
  }

  update(id: number, changes: TargetChanges): Observable<Target> {
    return this.http.put<Target>(`${URL}/${id}`, changes);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${URL}/${id}`);
  }
}
