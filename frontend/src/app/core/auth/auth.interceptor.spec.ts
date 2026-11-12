import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };
const NO_CONTENT = { status: 204, statusText: 'No Content' };

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let navigate: ReturnType<typeof vi.fn>;
  let auth: AuthService;

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-value; path=/';
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate, url: '/groups?x=1' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('adds no credentials of its own: the browser sends the session cookies', () => {
    client.get('/api/groups').subscribe();

    const request = http.expectOne('/api/groups');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush([]);
  });

  it('lets successful calls and other errors through untouched', () => {
    const errors: number[] = [];
    client.get('/api/groups').subscribe({ error: (e) => errors.push(e.status) });

    http.expectOne('/api/groups').flush(null, { status: 500, statusText: 'Server Error' });

    expect(errors).toEqual([500]);
    http.expectNone('/api/auth/refresh');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('refreshes the session after a 401 and repeats the call once', () => {
    let result: unknown;
    client.get('/api/groups').subscribe((value) => (result = value));

    http.expectOne('/api/groups').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, NO_CONTENT);
    http.expectOne('/api/groups').flush(['g1']);

    expect(result).toEqual(['g1']);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('repeats a write with its original body', () => {
    client.post('/api/groups', { name: 'Trip' }).subscribe();

    http.expectOne('/api/groups').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, NO_CONTENT);
    const retry = http.expectOne('/api/groups');

    expect(retry.request.method).toBe('POST');
    expect(retry.request.body).toEqual({ name: 'Trip' });
    retry.flush({});
  });

  it('shares a single refresh between calls that were refused at the same time', () => {
    const results: unknown[] = [];
    client.get('/api/groups').subscribe((v) => results.push(v));
    client.get('/api/users/me').subscribe((v) => results.push(v));

    http.expectOne('/api/groups').flush(null, UNAUTHORIZED);
    http.expectOne('/api/users/me').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, NO_CONTENT);
    http.expectOne('/api/groups').flush('groups');
    http.expectOne('/api/users/me').flush('me');

    expect(results.sort()).toEqual(['groups', 'me']);
  });

  it('ends the session and goes to the login page when the repeated call is refused too', () => {
    const errors: number[] = [];
    client.get('/api/groups').subscribe({ error: (e) => errors.push(e.status) });

    http.expectOne('/api/groups').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
    http.expectOne('/api/groups').flush(null, UNAUTHORIZED);

    expect(errors).toEqual([401]);
    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/groups?x=1' },
    });
  });

  it('still tries once more when the refresh failed but another tab renewed the cookies', () => {
    let result: unknown;
    client.get('/api/groups').subscribe((value) => (result = value));

    http.expectOne('/api/groups').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);
    http.expectOne('/api/groups').flush(['g1']);

    expect(result).toEqual(['g1']);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not log the user out because of an unrelated error on the repeated call', () => {
    const errors: number[] = [];
    client.get('/api/groups').subscribe({ error: (e) => errors.push(e.status) });

    http.expectOne('/api/groups').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, NO_CONTENT);
    http.expectOne('/api/groups').flush(null, { status: 500, statusText: 'Server Error' });

    expect(errors).toEqual([500]);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('never tries to refresh because of the auth endpoints themselves', () => {
    client.post('/api/auth/login', {}).subscribe({ error: () => undefined });

    http.expectOne('/api/auth/login').flush(null, UNAUTHORIZED);

    http.expectNone('/api/auth/refresh');
  });

  it('leaves other origins and paths alone', () => {
    client.get('https://example.com/api/groups').subscribe({ error: () => undefined });
    client.get('/assets/logo.svg').subscribe({ error: () => undefined });

    http.expectOne('https://example.com/api/groups').flush(null, UNAUTHORIZED);
    http.expectOne('/assets/logo.svg').flush(null, UNAUTHORIZED);

    http.expectNone('/api/auth/refresh');
  });

  it('lets a call that handles the session itself fail without any refresh or redirect', () => {
    auth.restore().subscribe();

    http.expectOne('/api/users/me').flush(null, UNAUTHORIZED);
    http.expectOne('/api/auth/refresh').flush(null, UNAUTHORIZED);

    expect(navigate).not.toHaveBeenCalled();
  });
});
