import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { User } from '../models';
import { AuthService } from './auth.service';

const USER: User = {
  id: '00000000-0000-0000-0000-000000000001',
  email: 'ann@example.com',
  displayName: 'Ann',
  createdAt: '2026-11-10T10:00:00Z',
};

function setCsrfCookie(present: boolean): void {
  document.cookie = present
    ? 'XSRF-TOKEN=csrf-value; path=/'
    : 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
}

describe('AuthService', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.fn>;
  let auth: AuthService;

  beforeEach(() => {
    setCsrfCookie(true);
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    http.verify();
    setCsrfCookie(false);
  });

  it('starts as a guest', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.user()).toBeNull();
  });

  describe('restore', () => {
    it('recognises a browser that already holds a valid session', () => {
      auth.restore().subscribe();

      http.expectOne('/api/users/me').flush(USER);

      expect(auth.isAuthenticated()).toBe(true);
      expect(auth.user()).toEqual(USER);
    });

    it('swaps the refresh cookie for a new session when the access cookie ran out', () => {
      auth.restore().subscribe();

      http.expectOne('/api/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });
      http.expectOne('/api/auth/refresh').flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne('/api/users/me').flush(USER);

      expect(auth.user()).toEqual(USER);
    });

    it('treats the visitor as a guest when there is no session at all, without failing', () => {
      let failed = false;
      auth.restore().subscribe({ error: () => (failed = true) });

      http.expectOne('/api/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });
      http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(failed).toBe(false);
      expect(auth.isAuthenticated()).toBe(false);
    });

    it('stays a guest when the server cannot be reached', () => {
      auth.restore().subscribe();

      http.expectOne('/api/users/me').error(new ProgressEvent('error'));
      http.expectOne('/api/auth/refresh').error(new ProgressEvent('error'));

      expect(auth.isAuthenticated()).toBe(false);
    });
  });

  describe('login', () => {
    it('posts the credentials and remembers who signed in', () => {
      auth.login('ann@example.com', 'secret-password').subscribe();

      const request = http.expectOne('/api/auth/login');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        email: 'ann@example.com',
        password: 'secret-password',
      });
      request.flush(USER);

      expect(auth.user()).toEqual(USER);
      expect(auth.isAuthenticated()).toBe(true);
    });

    it('asks for a CSRF token first when the browser has none yet', () => {
      setCsrfCookie(false);

      auth.login('ann@example.com', 'secret-password').subscribe();

      http.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne('/api/auth/login').flush(USER);
      expect(auth.isAuthenticated()).toBe(true);
    });

    it('does not ask again when the CSRF cookie is there', () => {
      auth.login('ann@example.com', 'x').subscribe();

      http.expectNone('/api/auth/csrf');
      http.expectOne('/api/auth/login').flush(USER);
    });

    it('leaves the user signed out when the credentials are wrong', () => {
      let failed = false;
      auth.login('ann@example.com', 'wrong').subscribe({ error: () => (failed = true) });

      http
        .expectOne('/api/auth/login')
        .flush(
          { detail: 'Invalid email or password' },
          { status: 401, statusText: 'Unauthorized' },
        );

      expect(failed).toBe(true);
      expect(auth.isAuthenticated()).toBe(false);
    });

    it('never puts anything into the storage a script on the page could read', () => {
      sessionStorage.clear();
      localStorage.clear();

      auth.login('ann@example.com', 'secret-password').subscribe();
      http.expectOne('/api/auth/login').flush(USER);

      expect(sessionStorage.length).toBe(0);
      expect(localStorage.length).toBe(0);
    });
  });

  describe('register', () => {
    it('creates the account and then signs in with the same credentials', () => {
      auth
        .register({ email: 'ann@example.com', displayName: 'Ann', password: 'a-long-password' })
        .subscribe();

      const register = http.expectOne('/api/auth/register');
      expect(register.request.body).toEqual({
        email: 'ann@example.com',
        displayName: 'Ann',
        password: 'a-long-password',
      });
      register.flush(USER, { status: 201, statusText: 'Created' });
      const login = http.expectOne('/api/auth/login');
      expect(login.request.body).toEqual({ email: 'ann@example.com', password: 'a-long-password' });
      login.flush(USER);

      expect(auth.isAuthenticated()).toBe(true);
    });

    it('does not sign in when the account cannot be created', () => {
      auth
        .register({ email: 'ann@example.com', displayName: 'Ann', password: 'a-long-password' })
        .subscribe({ error: () => undefined });

      http
        .expectOne('/api/auth/register')
        .flush({ detail: 'exists' }, { status: 409, statusText: 'Conflict' });

      http.expectNone('/api/auth/login');
      expect(auth.isAuthenticated()).toBe(false);
    });
  });

  describe('refresh', () => {
    it('posts to the refresh endpoint', () => {
      auth.refresh().subscribe();

      const request = http.expectOne('/api/auth/refresh');
      expect(request.request.method).toBe('POST');
      request.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('shares one request between callers that ask at the same time', () => {
      let completed = 0;
      auth.refresh().subscribe({ complete: () => completed++ });
      auth.refresh().subscribe({ complete: () => completed++ });
      auth.refresh().subscribe({ complete: () => completed++ });

      http.expectOne('/api/auth/refresh').flush(null, { status: 204, statusText: 'No Content' });

      expect(completed).toBe(3);
    });

    it('makes a new request once the earlier one has finished', () => {
      auth.refresh().subscribe();
      http.expectOne('/api/auth/refresh').flush(null, { status: 204, statusText: 'No Content' });

      auth.refresh().subscribe();

      http.expectOne('/api/auth/refresh').flush(null, { status: 204, statusText: 'No Content' });
    });

    it('lets every waiting caller know when the refresh failed, and can try again later', () => {
      const errors: unknown[] = [];
      auth.refresh().subscribe({ error: (e) => errors.push(e) });
      auth.refresh().subscribe({ error: (e) => errors.push(e) });

      http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(errors).toHaveLength(2);
      auth.refresh().subscribe({ error: () => undefined });
      http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('logout', () => {
    function signIn(): void {
      auth.login('ann@example.com', 'x').subscribe();
      http.expectOne('/api/auth/login').flush(USER);
    }

    it('ends the session on the server, forgets the user and goes to the login page', () => {
      signIn();

      auth.logout();

      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login']);
      expect(http.expectOne('/api/auth/logout').request.method).toBe('POST');
    });

    it('signs out locally even when the server cannot be reached', () => {
      signIn();

      auth.logout();
      http.expectOne('/api/auth/logout').error(new ProgressEvent('error'));

      expect(auth.isAuthenticated()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  it('endSession forgets the user without any request', () => {
    auth.login('ann@example.com', 'x').subscribe();
    http.expectOne('/api/auth/login').flush(USER);

    auth.endSession();

    expect(auth.user()).toBeNull();
  });
});
