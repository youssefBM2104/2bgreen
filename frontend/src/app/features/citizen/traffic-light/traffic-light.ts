import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, Observable, of, switchMap, timer } from 'rxjs';
import {
  TrafficLightLevel,
  TrafficLightReading,
  TrafficLightService,
} from './traffic-light.service';

/** Une page laissée ouverte ne doit pas continuer à afficher une couleur périmée. */
export const REFRESH_MS = 5 * 60 * 1000;

type State =
  | { kind: 'loading' }
  | { kind: 'ready'; reading: TrafficLightReading }
  | { kind: 'noData' }
  | { kind: 'unavailable' };

const MESSAGES: Record<TrafficLightLevel, { title: string; hint: string }> = {
  GREEN: {
    title: 'Réseau propre',
    hint: 'Bon moment pour lancer vos appareils : lave-vaisselle, lave-linge, recharge.',
  },
  AMBER: {
    title: 'Réseau moyen',
    hint: 'Si ça ne presse pas, attendez que le réseau soit plus propre.',
  },
  RED: {
    title: 'Réseau carboné',
    hint: 'Évitez si possible de lancer vos appareils maintenant.',
  },
  UNKNOWN: {
    title: 'Niveau inconnu',
    hint: "L'intensité CO₂ n'est pas disponible pour cette mesure.",
  },
};

// France uniquement : l'heure affichée est celle de Paris, quel que soit le fuseau de la machine.
const PARIS_TIME = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris',
  dateStyle: 'short',
  timeStyle: 'short',
});

@Component({
  selector: 'app-traffic-light',
  templateUrl: './traffic-light.html',
  styleUrl: './traffic-light.scss',
})
export class TrafficLight {
  private readonly service = inject(TrafficLightService);

  protected readonly messages = MESSAGES;

  protected readonly state = toSignal(timer(0, REFRESH_MS).pipe(switchMap(() => this.load())), {
    initialValue: { kind: 'loading' } as State,
  });

  protected readonly reading = computed(() => {
    const state = this.state();
    return state.kind === 'ready' ? state.reading : null;
  });

  protected time(observedAt: string): string {
    return PARIS_TIME.format(new Date(observedAt));
  }

  private load(): Observable<State> {
    return this.service.current().pipe(
      map((reading): State => ({ kind: 'ready', reading })),
      catchError((error: HttpErrorResponse) =>
        of<State>(error.error?.code === 'NO_DATA' ? { kind: 'noData' } : { kind: 'unavailable' }),
      ),
    );
  }
}
