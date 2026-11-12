import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, Observable, of, switchMap, timer } from 'rxjs';
import {
  Balance,
  DashboardReading,
  Production,
  RegionalDashboard as Dashboard,
  RegionalDashboardService,
} from './regional-dashboard.service';

/** Le régional est relu toutes les heures : rafraîchir plus souvent ne montrerait rien de neuf, mais reste léger. */
export const REFRESH_MS = 5 * 60 * 1000;

/** La note de méthode du CO₂ régional (G7), en attendant la vue dédiée (U15). */
export const CO2_METHOD_URL =
  'https://github.com/youssefBM2104/2bgreen/blob/main/docs/data/regional-co2.md';

type State =
  | { kind: 'loading' }
  | { kind: 'ready'; dashboard: Dashboard }
  | { kind: 'noRegion' }
  | { kind: 'noData' }
  | { kind: 'unavailable' };

const SOURCES: { key: keyof Production; label: string }[] = [
  { key: 'nuclear', label: 'Nucléaire' },
  { key: 'thermal', label: 'Thermique fossile (gaz, charbon, fioul)' },
  { key: 'wind', label: 'Éolien' },
  { key: 'solar', label: 'Solaire' },
  { key: 'hydro', label: 'Hydraulique' },
  { key: 'bioenergy', label: 'Bioénergies' },
];

const BALANCES: Record<Balance, string> = {
  IMPORTING: 'La région importe',
  EXPORTING: 'La région exporte',
  BALANCED: 'La région produit ce qu’elle consomme',
};

// France uniquement : l'heure affichée est celle de Paris, quel que soit le fuseau de la machine.
const PARIS_TIME = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris',
  dateStyle: 'short',
  timeStyle: 'short',
});

const WHOLE = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

const ONE_DECIMAL = new Intl.NumberFormat('fr-FR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

/** Ce que voit l'élu : sa région maintenant, et la France à la même heure pour la situer (U11). */
@Component({
  selector: 'app-regional-dashboard',
  templateUrl: './regional-dashboard.html',
  styleUrl: './regional-dashboard.scss',
})
export class RegionalDashboard {
  private readonly service = inject(RegionalDashboardService);

  protected readonly sources = SOURCES;

  protected readonly balances = BALANCES;

  protected readonly methodUrl = CO2_METHOD_URL;

  protected readonly state = toSignal(timer(0, REFRESH_MS).pipe(switchMap(() => this.load())), {
    initialValue: { kind: 'loading' } as State,
  });

  protected readonly dashboard = computed(() => {
    const state = this.state();
    return state.kind === 'ready' ? state.dashboard : null;
  });

  protected time(instant: string): string {
    return PARIS_TIME.format(new Date(instant));
  }

  /** MW. Une valeur absente s'affiche comme telle, jamais comme 0. */
  protected mw(value: number | null): string {
    return value === null ? '—' : `${WHOLE.format(value)} MW`;
  }

  protected percent(value: number | null): string {
    return value === null ? '—' : `${ONE_DECIMAL.format(value)} %`;
  }

  protected co2(reading: DashboardReading): string {
    return reading.co2Intensity === null ? '—' : `${WHOLE.format(reading.co2Intensity)} gCO₂éq/kWh`;
  }

  /** `PT4H` -> « 4 h ». Seuls les formats heures/minutes sont attendus d'une limite d'ancienneté. */
  protected duration(iso: string): string {
    const match = /^PT(?:(\d+)H)?(?:(\d+)M)?$/.exec(iso);
    if (!match) {
      return iso;
    }
    const [, hours, minutes] = match;
    return [hours && `${hours} h`, minutes && `${minutes} min`].filter(Boolean).join(' ');
  }

  private load(): Observable<State> {
    return this.service.dashboard().pipe(
      map((dashboard): State => ({ kind: 'ready', dashboard })),
      catchError((error: HttpErrorResponse) => of<State>(this.failure(error))),
    );
  }

  private failure(error: HttpErrorResponse): State {
    switch (error.error?.code) {
      case 'REGION_NOT_ASSIGNED':
        return { kind: 'noRegion' };
      case 'NO_DATA':
        return { kind: 'noData' };
      default:
        return { kind: 'unavailable' };
    }
  }
}
