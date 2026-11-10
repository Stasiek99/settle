import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { RegisterRequest, TokenResponse, User } from '../models';
import { TokenStore } from './token-store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly store = new TokenStore();

  private readonly tokenState = signal<string | null>(this.store.read());
  private readonly userState = signal<User | null>(null);

  readonly token = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenState() !== null);

  login(email: string, password: string): Observable<User> {
    return this.http.post<TokenResponse>(`${this.baseUrl}/auth/login`, { email, password }).pipe(
      tap((response) => {
        this.store.write(response.accessToken, response.expiresIn);
        this.tokenState.set(response.accessToken);
      }),
      switchMap(() => this.loadUser()),
    );
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<User>(`${this.baseUrl}/auth/register`, request)
      .pipe(switchMap(() => this.login(request.email, request.password)));
  }

  loadUser(): Observable<User> {
    return this.http
      .get<User>(`${this.baseUrl}/users/me`)
      .pipe(tap((user) => this.userState.set(user)));
  }

  logout(): void {
    this.endSession();
    void this.router.navigate(['/login']);
  }

  endSession(): void {
    this.store.clear();
    this.tokenState.set(null);
    this.userState.set(null);
  }
}
