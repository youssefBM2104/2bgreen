import { Component } from '@angular/core';
import { TrafficLight } from './traffic-light/traffic-light';

@Component({
  selector: 'app-citizen-home',
  imports: [TrafficLight],
  template: `
    <h1>Espace citoyen</h1>
    <p>Consommez quand le réseau électrique est le plus propre.</p>
    <app-traffic-light />
  `,
})
export class CitizenHome {}
