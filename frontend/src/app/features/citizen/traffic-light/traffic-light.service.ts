import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PLATFORM_URL } from '../../../core/auth/auth.config';

export type TrafficLightLevel = 'GREEN' | 'AMBER' | 'RED' | 'UNKNOWN';

export interface TrafficLightReading {
  level: TrafficLightLevel;
  /** gCO2eq/kWh. Une valeur absente est inconnue, jamais 0. */
  co2Intensity: number | null;
  observedAt: string;
  stale: boolean;
}

@Injectable({ providedIn: 'root' })
export class TrafficLightService {
  private readonly http = inject(HttpClient);

  current(): Observable<TrafficLightReading> {
    return this.http.get<TrafficLightReading>(`${PLATFORM_URL}/api/v1/advisory/traffic-light`);
  }
}
