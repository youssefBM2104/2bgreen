import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ProgressChart } from './progress-chart';
import {
  Indicator,
  MAX_TARGETS,
  Target,
  TargetStatus,
  TargetsService,
} from './targets.service';

export const INDICATORS: Record<Indicator, string> = {
  RENEWABLE_SHARE: 'Part renouvelable',
  COVERAGE_RATE: 'Taux de couverture',
  CONSUMPTION_DROP: 'Baisse de la consommation',
  ESTIMATED_CO2: 'CO₂ moyen estimé',
};

const STATUSES: Record<TargetStatus, string> = {
  AHEAD: 'En avance',
  ON_TRACK: 'Dans la trajectoire',
  LATE: 'En retard',
};

const PROBLEMS: Record<string, string> = {
  load: 'Impossible de lire vos objectifs. Réessayez dans quelques minutes.',
  TARGET_LIMIT_REACHED: `Vous avez déjà ${MAX_TARGETS} objectifs. Supprimez-en un pour en créer un autre.`,
  REFERENCE_UNAVAILABLE:
    "Pas assez de données pour cette année de référence dans votre région. Choisissez-en une autre.",
  REGION_NOT_ASSIGNED: "Votre compte n'est rattaché à aucune région. Demandez à un administrateur.",
  TARGET_NOT_FOUND: "Cet objectif n'existe plus.",
  save: "L'opération n'a pas pu aboutir. Réessayez.",
};

const NUMBER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });
const DATE = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeZone: 'UTC' });

/** Page « Mes objectifs » de l'élu (U13) : fixer des objectifs pour sa région et suivre leur avancement. */
@Component({
  selector: 'app-targets',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ProgressChart,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './targets.html',
  styleUrl: './targets.scss',
})
export class Targets {
  private readonly service = inject(TargetsService);

  protected readonly indicators = Object.entries(INDICATORS) as [Indicator, string][];
  protected readonly indicatorLabels = INDICATORS;
  protected readonly statuses = STATUSES;
  protected readonly lastYear = new Date().getFullYear() - 1;
  protected readonly maxTargets = MAX_TARGETS;

  /** `null` tant que la liste n'est pas arrivée. */
  protected readonly targets = signal<Target[] | null>(null);
  protected readonly problem = signal<string | null>(null);
  protected readonly saving = signal(false);
  /** L'objectif en cours de modification. */
  protected readonly editing = signal<number | null>(null);
  protected readonly full = computed(() => (this.targets()?.length ?? 0) >= MAX_TARGETS);

  protected readonly form = new FormGroup({
    indicator: new FormControl<Indicator>('RENEWABLE_SHARE', { nonNullable: true }),
    referenceYear: new FormControl<number | null>(this.lastYear, [Validators.required]),
    targetValue: new FormControl<number | null>(null, [Validators.required]),
    deadline: new FormControl<string | null>(null, [Validators.required]),
    tolerance: new FormControl<number | null>(null, [Validators.min(0.01)]),
  });

  protected readonly edit = new FormGroup({
    targetValue: new FormControl<number | null>(null, [Validators.required]),
    deadline: new FormControl<string | null>(null, [Validators.required]),
    tolerance: new FormControl<number | null>(null, [Validators.min(0.01)]),
  });

  constructor() {
    this.load();
  }

  protected value(value: number | null | undefined, unit: string): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return unit === '%' ? `${NUMBER.format(value)} %` : `${NUMBER.format(value)} gCO₂éq/kWh`;
  }

  protected date(day: string): string {
    return DATE.format(new Date(day));
  }

  protected number(value: number): string {
    return NUMBER.format(value);
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.run(
      this.service.create({
        indicator: v.indicator,
        referenceYear: v.referenceYear!,
        targetValue: v.targetValue!,
        deadline: v.deadline!,
        tolerance: v.tolerance ?? null,
      }),
      () => this.form.patchValue({ targetValue: null, deadline: null, tolerance: null }),
    );
  }

  protected startEdit(target: Target): void {
    this.edit.setValue({
      targetValue: target.targetValue,
      deadline: target.deadline,
      tolerance: target.tolerance,
    });
    this.editing.set(target.id);
  }

  protected saveEdit(id: number): void {
    if (this.edit.invalid) {
      this.edit.markAllAsTouched();
      return;
    }
    const v = this.edit.getRawValue();
    this.run(
      this.service.update(id, {
        targetValue: v.targetValue!,
        deadline: v.deadline!,
        tolerance: v.tolerance ?? null,
      }),
      () => this.editing.set(null),
    );
  }

  protected remove(id: number): void {
    this.run(this.service.delete(id), () => undefined);
  }

  private run(call: Observable<unknown>, done: () => void): void {
    this.saving.set(true);
    this.problem.set(null);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        done();
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        const code = error.error?.code as string | undefined;
        if (code === 'INVALID_TARGET') {
          this.problem.set(`Objectif refusé : ${error.error?.detail ?? 'valeurs invalides'}.`);
        } else {
          this.problem.set(PROBLEMS[code ?? ''] ?? PROBLEMS['save']);
        }
      },
    });
  }

  private load(): void {
    this.service.list().subscribe({
      next: (targets) => this.targets.set(targets),
      error: (error: HttpErrorResponse) => {
        this.targets.set([]);
        this.problem.set(PROBLEMS[error.error?.code ?? ''] ?? PROBLEMS['load']);
      },
    });
  }
}
