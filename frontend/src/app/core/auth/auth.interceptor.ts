import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, of, switchMap, throwError } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { AuthService, HANDLES_SESSION_ITSELF } from './auth.service';

function isUnauthorized(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 401;
}

/**
 * The session lives in httpOnly cookies the browser attaches by itself, so there is no token to add.
 * What is left to do: when a call is refused because the short-lived access cookie ran out, swap the
 * refresh cookie for a new session and repeat the call once.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const baseUrl = inject(API_BASE_URL);
  const auth = inject(AuthService);
  const router = inject(Router);

  const isApiCall = request.url.startsWith(`${baseUrl}/`);
  const isAuthCall = request.url.startsWith(`${baseUrl}/auth/`);
  if (!isApiCall || isAuthCall || request.context.get(HANDLES_SESSION_ITSELF)) {
    return next(request);
  }

  return next(request).pipe(
    catchError((error: unknown) => {
      if (!isUnauthorized(error)) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        // A refused refresh is worth one retry: another tab may have just renewed the cookies. Any other
        // failure (rate limit, server down) says nothing about the session, so it must not end it.
        catchError((refreshError: unknown) =>
          isUnauthorized(refreshError) ? of(undefined) : throwError(() => refreshError),
        ),
        switchMap(() => next(request)),
        catchError((retryError: unknown) => {
          if (isUnauthorized(retryError)) {
            auth.endSession();
            void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
          }
          return throwError(() => retryError);
        }),
      );
    }),
  );
};
