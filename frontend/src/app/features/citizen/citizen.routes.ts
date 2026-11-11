import { Routes } from '@angular/router';
import { CitizenHome } from './citizen-home';

export const CITIZEN_ROUTES: Routes = [
  { path: '', component: CitizenHome },
  {
    path: 'alertes',
    title: 'Mes alertes · 2bGreen',
    loadComponent: () => import('./alerts/alerts').then((m) => m.Alerts),
  },
];
