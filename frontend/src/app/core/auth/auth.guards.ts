import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/groups']) : true;
};

export function safeReturnUrl(candidate: string | null | undefined): string {
  const isInternalPath =
    typeof candidate === 'string' &&
    candidate.startsWith('/') &&
    !candidate.startsWith('//') &&
    !candidate.includes('\\');
  return isInternalPath ? candidate : '/groups';
}
