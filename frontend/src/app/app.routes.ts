import { Routes } from '@angular/router';
import { homeGuard, roleGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  // Pas de composant : le guard redirige toujours vers l'espace du rôle.
  { path: '', pathMatch: 'full', canActivate: [homeGuard], children: [] },
  {
    path: 'citoyen',
    title: 'Citoyen · 2bGreen',
    canActivate: [roleGuard('citizen')],
    loadChildren: () => import('./features/citizen/citizen.routes').then((m) => m.CITIZEN_ROUTES),
  },
  {
    path: 'devops',
    title: 'DevOps · 2bGreen',
    canActivate: [roleGuard('devops')],
    loadChildren: () => import('./features/devops/devops.routes').then((m) => m.DEVOPS_ROUTES),
  },
  {
    path: 'elu',
    title: 'Élu régional · 2bGreen',
    canActivate: [roleGuard('regional_official')],
    loadChildren: () =>
      import('./features/official/official.routes').then((m) => m.OFFICIAL_ROUTES),
  },
  {
    path: 'acces-refuse',
    title: 'Accès refusé · 2bGreen',
    loadComponent: () => import('./shared/access-denied/access-denied').then((m) => m.AccessDenied),
  },
  {
    path: '**',
    title: 'Page introuvable · 2bGreen',
    loadComponent: () => import('./shared/not-found/not-found').then((m) => m.NotFound),
  },
];
