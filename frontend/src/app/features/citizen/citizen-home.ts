import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TrafficLight } from './traffic-light/traffic-light';

@Component({
  selector: 'app-citizen-home',
  imports: [MatButtonModule, RouterLink, TrafficLight],
  template: `
    <h1>Espace citoyen</h1>
    <p>Consommez quand le réseau électrique est le plus propre.</p>
    <app-traffic-light />
    <p>
      <a mat-stroked-button routerLink="alertes">Mes alertes « réseau propre »</a>
    </p>
  `,
})
export class CitizenHome {}
