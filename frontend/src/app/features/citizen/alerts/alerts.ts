import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { TrafficLightService } from '../traffic-light/traffic-light.service';
import { Alert, AlertsService, MAX_ALERTS, MAX_THRESHOLD, MIN_THRESHOLD } from './alerts.service';

type Problem = 'load' | 'limit' | 'invalid' | 'save' | null;

const PROBLEMS: Record<Exclude<Problem, null>, string> = {
  load: 'Impossible de lire vos alertes. Réessayez dans quelques minutes.',
  limit: `Vous avez déjà ${MAX_ALERTS} alertes. Supprimez-en une pour en créer une autre.`,
  invalid: `Le seuil doit être un nombre entier entre ${MIN_THRESHOLD} et ${MAX_THRESHOLD}.`,
  save: "La modification n'a pas pu être enregistrée. Réessayez.",
};

/** Page « Mes alertes » (U2) : créer, mettre en pause, réactiver et supprimer ses alertes « réseau propre ». */
@Component({
  selector: 'app-alerts',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './alerts.html',
  styleUrl: './alerts.scss',
})
export class Alerts {
  private readonly service = inject(AlertsService);

  protected readonly min = MIN_THRESHOLD;
  protected readonly max = MAX_THRESHOLD;
  protected readonly maxAlerts = MAX_ALERTS;

  /** `null` tant que la liste n'est pas arrivée. */
  protected readonly alerts = signal<Alert[] | null>(null);
  protected readonly problem = signal<Problem>(null);
  protected readonly message = computed(() => {
    const problem = this.problem();
    return problem ? PROBLEMS[problem] : null;
  });
  protected readonly full = computed(() => (this.alerts()?.length ?? 0) >= MAX_ALERTS);

  /** Les seuils du feu tricolore, proposés comme valeurs. Sans eux le formulaire marche quand même. */
  protected readonly suggestions = signal<{ label: string; value: number }[]>([]);

  protected readonly threshold = new FormControl<number | null>(null, [
    Validators.required,
    Validators.min(MIN_THRESHOLD),
    Validators.max(MAX_THRESHOLD),
    Validators.pattern(/^\d+$/),
  ]);
  protected readonly form = new FormGroup({ threshold: this.threshold });

  constructor() {
    this.service
      .list()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (alerts) => this.alerts.set(alerts),
        error: () => this.problem.set('load'),
      });
    inject(TrafficLightService)
      .limits()
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(),
      )
      .subscribe((limits) => {
        if (!limits) {
          return;
        }
        this.suggestions.set([
          { label: 'Feu vert', value: limits.greenBelow },
          { label: 'Feu orange', value: limits.amberBelow },
        ]);
        if (this.threshold.pristine) {
          this.threshold.setValue(limits.greenBelow);
        }
      });
  }

  protected suggest(value: number): void {
    this.threshold.setValue(value);
    this.threshold.markAsDirty();
  }

  protected create(): void {
    const threshold = this.threshold.value;
    if (this.form.invalid || threshold === null || this.full()) {
      this.threshold.markAsTouched();
      return;
    }
    this.problem.set(null);
    this.service.create(threshold).subscribe({
      next: (created) => this.alerts.update((alerts) => [...(alerts ?? []), created]),
      error: (error: HttpErrorResponse) => this.problem.set(problemOf(error)),
    });
  }

  /** Affiché tout de suite, puis annulé si platform refuse. */
  protected setActive(alert: Alert, active: boolean): void {
    this.problem.set(null);
    this.put({ ...alert, active });
    this.service.replace(alert, { active }).subscribe({
      next: (saved) => this.put(saved),
      error: () => {
        this.put(alert);
        this.problem.set('save');
      },
    });
  }

  protected remove(alert: Alert): void {
    this.problem.set(null);
    this.service.remove(alert).subscribe({
      next: () => this.alerts.update((alerts) => (alerts ?? []).filter((a) => a.id !== alert.id)),
      error: () => this.problem.set('save'),
    });
  }

  private put(alert: Alert): void {
    this.alerts.update((alerts) => (alerts ?? []).map((a) => (a.id === alert.id ? alert : a)));
  }
}

function problemOf(error: HttpErrorResponse): Problem {
  switch (error.error?.code) {
    case 'ALERT_LIMIT_REACHED':
      return 'limit';
    case 'INVALID_ALERT':
      return 'invalid';
    default:
      return 'save';
  }
}
