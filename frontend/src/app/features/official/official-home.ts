import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { RegionalDashboard } from './regional-dashboard/regional-dashboard';

@Component({
  selector: 'app-official-home',
  imports: [MatButtonModule, RegionalDashboard, RouterLink],
  template: `
    <h1>Espace élu régional</h1>
    <p>Suivez la transition énergétique de votre région et ses objectifs.</p>
    <p>
      <a mat-stroked-button routerLink="objectifs">Mes objectifs</a>
    </p>
    <app-regional-dashboard />
  `,
})
export class OfficialHome {}
