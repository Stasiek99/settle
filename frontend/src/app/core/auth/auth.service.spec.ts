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

describe('AuthService', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.fn>;

  function create(): AuthService {
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  beforeEach(() => sessionStorage.clear());
  afterEach(() => http.verify());

  it('starts signed out', () => {
    const auth = create();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.user()).toBeNull();
  });

  it('login posts the credentials, keeps the token and loads the current user', () => {
    const auth = create();
    let result: User | undefined;

    auth.login('ann@example.com', 'secret-password').subscribe((user) => (result = user));

    const login = http.expectOne('/api/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.body).toEqual({ email: 'ann@example.com', password: 'secret-password' });
    login.flush({ accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 3600 });
    http.expectOne('/api/users/me').flush(USER);

    expect(result).toEqual(USER);
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.token()).toBe('jwt-1');
    expect(auth.user()).toEqual(USER);
  });

  it('keeps the session across a reload through sessionStorage', () => {
    const first = create();
    first.login('ann@example.com', 'x').subscribe();
    http
      .expectOne('/api/auth/login')
      .flush({ accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 3600 });
    http.expectOne('/api/users/me').flush(USER);

    TestBed.resetTestingModule();
    const second = create();

    expect(second.isAuthenticated()).toBe(true);
    expect(second.token()).toBe('jwt-1');
  });

  it('a failed login leaves the user signed out', () => {
    const auth = create();
    let failed = false;

    auth.login('ann@example.com', 'wrong').subscribe({ error: () => (failed = true) });
    http
      .expectOne('/api/auth/login')
      .flush({ detail: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('register creates the account and then signs in with the same credentials', () => {
    const auth = create();

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
    login.flush({ accessToken: 'jwt-2', tokenType: 'Bearer', expiresIn: 3600 });
    http.expectOne('/api/users/me').flush(USER);

    expect(auth.isAuthenticated()).toBe(true);
  });

  it('register does not sign in when the account cannot be created', () => {
    const auth = create();

    auth
      .register({ email: 'ann@example.com', displayName: 'Ann', password: 'a-long-password' })
      .subscribe({
        error: () => undefined,
      });
    http
      .expectOne('/api/auth/register')
      .flush({ detail: 'exists' }, { status: 409, statusText: 'Conflict' });

    http.expectNone('/api/auth/login');
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('logout clears the session and goes to the login page', () => {
    const auth = create();
    auth.login('ann@example.com', 'x').subscribe();
    http
      .expectOne('/api/auth/login')
      .flush({ accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 3600 });
    http.expectOne('/api/users/me').flush(USER);

    auth.logout();

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.user()).toBeNull();
    expect(sessionStorage.getItem('settle.session')).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
