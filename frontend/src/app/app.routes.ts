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
      {
        path: 'groups/:groupId',
        loadComponent: () => import('./features/group/group-page').then((m) => m.GroupPage),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'expenses' },
          {
            path: 'expenses',
            loadComponent: () =>
              import('./features/group/expenses/expenses-page').then((m) => m.ExpensesPage),
            title: 'Expenses · Settle',
          },
          {
            path: 'balances',
            loadComponent: () =>
              import('./features/group/balances/balances-page').then((m) => m.BalancesPage),
            title: 'Balances · Settle',
          },
          {
            path: 'settlements',
            loadComponent: () =>
              import('./features/group/settlements/settlements-page').then(
                (m) => m.SettlementsPage,
              ),
            title: 'Settlements · Settle',
          },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
