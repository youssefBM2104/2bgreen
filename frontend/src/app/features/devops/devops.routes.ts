import { Routes } from '@angular/router';
import { DevopsHome } from './devops-home';

export const DEVOPS_ROUTES: Routes = [
  { path: '', component: DevopsHome },
  {
    path: 'cles',
    title: "Mes clés d'API · 2bGreen",
    loadComponent: () => import('./api-keys/api-keys').then((m) => m.ApiKeys),
  },
];
