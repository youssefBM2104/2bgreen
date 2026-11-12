import { Component, computed, input } from '@angular/core';
import { TargetPoint } from './targets.service';

const WIDTH = 600;
const HEIGHT = 180;
const PAD = { top: 12, right: 12, bottom: 24, left: 48 };

const SHORT_DATE = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeZone: 'UTC' });
const NUMBER = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });

/**
 * Progression d'un objectif dans le temps (U13) : la valeur mesurée (trait plein) et la trajectoire attendue
 * (pointillés), sur un seul axe. Chaque point a son infobulle ; le tableau de la page donne les mêmes chiffres.
 */
@Component({
  selector: 'app-progress-chart',
  template: `
    <div class="legend" aria-hidden="true">
      <span class="key value"></span> Valeur (12 mois glissants)
      <span class="key expected"></span> Trajectoire attendue
    </div>
    <svg
      [attr.viewBox]="'0 0 ' + width + ' ' + height"
      role="img"
      [attr.aria-label]="label()"
    >
      <line class="axis" [attr.x1]="pad.left" [attr.x2]="width - pad.right" [attr.y1]="y(scale().min)" [attr.y2]="y(scale().min)" />
      <text class="tick" [attr.x]="pad.left - 6" [attr.y]="y(scale().max) + 4" text-anchor="end">{{ number(scale().max) }}</text>
      <text class="tick" [attr.x]="pad.left - 6" [attr.y]="y(scale().min) + 4" text-anchor="end">{{ number(scale().min) }}</text>
      <text class="tick" [attr.x]="pad.left" [attr.y]="height - 6">{{ date(first().day) }}</text>
      @if (points().length > 1) {
        <text class="tick" [attr.x]="width - pad.right" [attr.y]="height - 6" text-anchor="end">{{ date(last().day) }}</text>
      }
      <polyline class="expected" [attr.points]="path('expected')" />
      <polyline class="value" [attr.points]="path('value')" />
      @for (point of points(); track point.day; let i = $index) {
        <circle class="expected-dot" [attr.cx]="x(i)" [attr.cy]="y(point.expected)" r="4" />
        <circle class="dot" [attr.cx]="x(i)" [attr.cy]="y(point.value)" r="4" />
        <circle class="hit" [attr.cx]="x(i)" [attr.cy]="y(point.value)" r="10">
          <title>{{ date(point.day) }} : {{ number(point.value) }} {{ unit() }} (attendu {{ number(point.expected) }})</title>
        </circle>
      }
    </svg>
  `,
  styles: `
    svg {
      width: 100%;
      max-width: 640px;
      height: auto;
      display: block;
    }
    .axis {
      stroke: var(--mat-sys-outline-variant);
    }
    .tick {
      font: var(--mat-sys-label-small);
      fill: var(--mat-sys-on-surface-variant);
    }
    polyline {
      fill: none;
      stroke-width: 2;
      vector-effect: non-scaling-stroke;
    }
    .value {
      stroke: var(--mat-sys-primary);
    }
    .expected {
      stroke: var(--mat-sys-outline);
      stroke-dasharray: 6 4;
    }
    .dot {
      fill: var(--mat-sys-primary);
      stroke: var(--mat-sys-surface);
      stroke-width: 2;
    }
    .expected-dot {
      fill: var(--mat-sys-surface);
      stroke: var(--mat-sys-outline);
      stroke-width: 2;
    }
    .hit {
      fill: transparent;
    }
    .legend {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }
    .key {
      display: inline-block;
      width: 18px;
      height: 0;
      border-top: 2px solid var(--mat-sys-primary);
      margin-left: 8px;
    }
    .key.expected {
      border-top: 2px dashed var(--mat-sys-outline);
    }
  `,
})
export class ProgressChart {
  readonly points = input.required<TargetPoint[]>();
  readonly unit = input.required<string>();

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly pad = PAD;

  protected readonly first = computed(() => this.points()[0]);
  protected readonly last = computed(() => this.points()[this.points().length - 1]);

  /** Un seul axe pour les deux séries, avec un peu de marge pour qu'aucun point ne colle au bord. */
  protected readonly scale = computed(() => {
    const values = this.points().flatMap((p) => [p.value, p.expected]);
    let min = Math.min(...values);
    let max = Math.max(...values);
    if (min === max) {
      min -= 1;
      max += 1;
    }
    const margin = (max - min) * 0.1;
    return { min: min - margin, max: max + margin };
  });

  protected readonly label = computed(() => {
    const last = this.last();
    return `Progression : ${this.number(last.value)} ${this.unit()} le ${this.date(last.day)}, attendu ${this.number(last.expected)}`;
  });

  protected x(index: number): number {
    const count = this.points().length;
    const span = WIDTH - PAD.left - PAD.right;
    return count === 1 ? PAD.left + span / 2 : PAD.left + (span * index) / (count - 1);
  }

  protected y(value: number): number {
    const { min, max } = this.scale();
    return PAD.top + ((max - value) / (max - min)) * (HEIGHT - PAD.top - PAD.bottom);
  }

  protected path(field: 'value' | 'expected'): string {
    return this.points()
      .map((point, i) => `${this.x(i)},${this.y(point[field])}`)
      .join(' ');
  }

  protected date(day: string): string {
    return SHORT_DATE.format(new Date(day));
  }

  protected number(value: number): string {
    return NUMBER.format(value);
  }
}
