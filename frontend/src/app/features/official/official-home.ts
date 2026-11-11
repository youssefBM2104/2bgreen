import { Component } from '@angular/core';
import { RegionalDashboard } from './regional-dashboard/regional-dashboard';

@Component({
  selector: 'app-official-home',
  imports: [RegionalDashboard],
  template: `
    <h1>Espace élu régional</h1>
    <p>Suivez la transition énergétique de votre région et ses objectifs.</p>
    <app-regional-dashboard />
  `,
})
export class OfficialHome {}
