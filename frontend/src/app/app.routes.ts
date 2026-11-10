import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./features/api-status/api-status').then((m) => m.ApiStatus),
    title: 'Settle',
  },
  { path: '**', redirectTo: '' },
];
