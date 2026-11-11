import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { ApiKey, ApiKeysService, MAX_KEYS, MAX_NAME_LENGTH } from './api-keys.service';

type Problem = 'load' | 'limit' | 'invalid' | 'save' | 'revoke' | null;

const PROBLEMS: Record<Exclude<Problem, null>, string> = {
  load: 'Impossible de lire vos clés. Réessayez dans quelques minutes.',
  limit: `Vous avez déjà ${MAX_KEYS} clés actives. Révoquez-en une pour en créer une autre.`,
  invalid: `Le nom doit faire de 1 à ${MAX_NAME_LENGTH} caractères.`,
  save: "La clé n'a pas pu être créée. Réessayez.",
  revoke: "La clé n'a pas pu être révoquée. Réessayez.",
};

// L'heure affichée est celle de Paris, quel que soit le fuseau de la machine.
const PARIS_TIME = new Intl.DateTimeFormat('fr-FR', {
  timeZone: 'Europe/Paris',
  dateStyle: 'short',
  timeStyle: 'short',
});

/**
 * Page « Mes clés d'API » (U10) : créer une clé (affichée une seule fois), suivre son usage du jour, la révoquer.
 */
@Component({
  selector: 'app-api-keys',
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule, RouterLink],
  templateUrl: './api-keys.html',
  styleUrl: './api-keys.scss',
})
export class ApiKeys {
  private readonly service = inject(ApiKeysService);

  protected readonly maxKeys = MAX_KEYS;
  protected readonly maxName = MAX_NAME_LENGTH;

  /** `null` tant que la liste n'est pas arrivée. */
  protected readonly keys = signal<ApiKey[] | null>(null);
  protected readonly problem = signal<Problem>(null);
  protected readonly message = computed(() => {
    const problem = this.problem();
    return problem ? PROBLEMS[problem] : null;
  });
  protected readonly full = computed(() => (this.keys()?.length ?? 0) >= MAX_KEYS);

  /** La clé qui vient d'être créée, montrée jusqu'à ce que l'utilisateur ferme l'encadré. Jamais stockée. */
  protected readonly created = signal<{ name: string; key: string } | null>(null);
  protected readonly copied = signal(false);
  /** La clé dont la révocation attend une confirmation. */
  protected readonly confirming = signal<number | null>(null);

  protected readonly name = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(MAX_NAME_LENGTH), Validators.pattern(/\S/)],
  });
  protected readonly form = new FormGroup({ name: this.name });

  constructor() {
    this.service
      .list()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (keys) => this.keys.set(keys),
        error: () => this.problem.set('load'),
      });
  }

  protected create(): void {
    if (this.form.invalid || this.full()) {
      this.name.markAsTouched();
      return;
    }
    this.problem.set(null);
    this.service.create(this.name.value.trim()).subscribe({
      next: ({ key, ...stored }) => {
        this.keys.update((keys) => [...(keys ?? []), stored]);
        this.created.set({ name: stored.name, key });
        this.copied.set(false);
        this.form.reset();
      },
      error: (error: HttpErrorResponse) => this.problem.set(problemOf(error)),
    });
  }

  protected async copy(key: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(key);
      this.copied.set(true);
    } catch {
      // Sans presse-papiers (page non sécurisée, refus du navigateur), la clé reste lisible et sélectionnable.
      this.copied.set(false);
    }
  }

  protected dismiss(): void {
    this.created.set(null);
  }

  protected revoke(key: ApiKey): void {
    this.problem.set(null);
    this.confirming.set(null);
    this.service.revoke(key).subscribe({
      next: () => this.keys.update((keys) => (keys ?? []).filter((k) => k.id !== key.id)),
      error: () => this.problem.set('revoke'),
    });
  }

  protected time(value: string | null): string {
    return value ? PARIS_TIME.format(new Date(value)) : 'jamais';
  }
}

function problemOf(error: HttpErrorResponse): Problem {
  switch (error.error?.code) {
    case 'API_KEY_LIMIT_REACHED':
      return 'limit';
    case 'INVALID_API_KEY_REQUEST':
      return 'invalid';
    default:
      return 'save';
  }
}
