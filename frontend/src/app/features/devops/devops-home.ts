import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-devops-home',
  imports: [MatButtonModule, RouterLink],
  template: `
    <h1>Espace DevOps</h1>
    <p>Planifiez vos traitements aux heures où l'électricité émet le moins de CO2.</p>
    <p>
      <a mat-stroked-button routerLink="cles">Mes clés d'API</a>
    </p>
  `,
})
export class DevopsHome {}
