import { DOCUMENT } from '@angular/common';
import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpContext, HttpContextToken } from '@angular/common/http';
import { Router } from '@angular/router';
import {
  catchError,
  EMPTY,
  finalize,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap,
} from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { RegisterRequest, User } from '../models';

/**
 * Requests that handle a missing or expired session themselves: the interceptor must not refresh,
 * retry or redirect for them.
 */
export const HANDLES_SESSION_ITSELF = new HttpContextToken<boolean>(() => false);

const CSRF_COOKIE = 'XSRF-TOKEN';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly document = inject(DOCUMENT);

  private readonly userState = signal<User | null>(null);
  private refreshInFlight: Observable<void> | null = null;

  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => this.userState() !== null);

  /**
   * Finds out whether the browser already holds a valid session. Never fails: no session simply means
   * the visitor is a guest.
   */
  restore(): Observable<void> {
    return this.currentUser().pipe(
      catchError(() => this.refresh().pipe(switchMap(() => this.currentUser()))),
      tap((user) => this.userState.set(user)),
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }

  login(email: string, password: string): Observable<User> {
    return this.ensureCsrfToken().pipe(
      switchMap(() => this.http.post<User>(`${this.baseUrl}/auth/login`, { email, password })),
      tap((user) => this.userState.set(user)),
    );
  }

  register(request: RegisterRequest): Observable<User> {
    return this.ensureCsrfToken().pipe(
      switchMap(() => this.http.post<User>(`${this.baseUrl}/auth/register`, request)),
      switchMap(() => this.login(request.email, request.password)),
    );
  }

  /** Swaps the refresh cookie for a new session. Concurrent callers share one request. */
  refresh(): Observable<void> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.ensureCsrfToken().pipe(
        switchMap(() =>
          this.http.post<void>(`${this.baseUrl}/auth/refresh`, null, {
            context: new HttpContext().set(HANDLES_SESSION_ITSELF, true),
          }),
        ),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight;
  }

  logout(): void {
    this.http
      .post<void>(`${this.baseUrl}/auth/logout`, null, {
        context: new HttpContext().set(HANDLES_SESSION_ITSELF, true),
      })
      .pipe(catchError(() => EMPTY))
      .subscribe();
    this.endSession();
    void this.router.navigate(['/login']);
  }

  endSession(): void {
    this.userState.set(null);
  }

  private currentUser(): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/users/me`, {
      context: new HttpContext().set(HANDLES_SESSION_ITSELF, true),
    });
  }

  private ensureCsrfToken(): Observable<unknown> {
    return this.hasCsrfCookie()
      ? of(undefined)
      : this.http.get<void>(`${this.baseUrl}/auth/csrf`, {
          context: new HttpContext().set(HANDLES_SESSION_ITSELF, true),
        });
  }

  private hasCsrfCookie(): boolean {
    return this.document.cookie
      .split(';')
      .some((cookie) => cookie.trim().startsWith(`${CSRF_COOKIE}=`));
  }
}
