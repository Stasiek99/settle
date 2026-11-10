import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing),
    title: 'Settle',
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
    title: 'Sign in · Settle',
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
    title: 'Create account · Settle',
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: 'groups',
        loadComponent: () => import('./features/groups/groups-page').then((m) => m.GroupsPage),
        title: 'Groups · Settle',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
