import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'citoyen' },
  {
    path: 'citoyen',
    title: 'Citoyen · 2bGreen',
    loadChildren: () => import('./features/citizen/citizen.routes').then((m) => m.CITIZEN_ROUTES),
  },
  {
    path: 'devops',
    title: 'DevOps · 2bGreen',
    loadChildren: () => import('./features/devops/devops.routes').then((m) => m.DEVOPS_ROUTES),
  },
  {
    path: 'elu',
    title: 'Élu régional · 2bGreen',
    loadChildren: () =>
      import('./features/official/official.routes').then((m) => m.OFFICIAL_ROUTES),
  },
  {
    path: '**',
    title: 'Page introuvable · 2bGreen',
    loadComponent: () => import('./shared/not-found/not-found').then((m) => m.NotFound),
  },
];
