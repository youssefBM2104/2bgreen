import { Routes } from '@angular/router';
import { OfficialHome } from './official-home';

export const OFFICIAL_ROUTES: Routes = [
  { path: '', component: OfficialHome },
  {
    path: 'objectifs',
    title: 'Mes objectifs · 2bGreen',
    loadComponent: () => import('./targets/targets').then((m) => m.Targets),
  },
];
