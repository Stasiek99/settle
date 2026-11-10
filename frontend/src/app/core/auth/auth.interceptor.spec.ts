import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let auth: { token: ReturnType<typeof vi.fn>; endSession: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.fn>;

  function setup(token: string | null): void {
    auth = { token: vi.fn().mockReturnValue(token), endSession: vi.fn() };
    navigate = vi.fn().mockResolvedValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: { navigate, url: '/groups?x=1' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  }

  afterEach(() => http.verify());

  it('adds the bearer token to API calls', () => {
    setup('jwt-1');

    client.get('/api/groups').subscribe();

    expect(http.expectOne('/api/groups').request.headers.get('Authorization')).toBe('Bearer jwt-1');
  });

  it('does not send the token to the auth endpoints', () => {
    setup('jwt-1');

    client.post('/api/auth/login', {}).subscribe();

    expect(http.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('does not send the token to other origins or paths', () => {
    setup('jwt-1');

    client.get('https://example.com/api/groups').subscribe();
    client.get('/assets/logo.svg').subscribe();

    expect(
      http.expectOne('https://example.com/api/groups').request.headers.has('Authorization'),
    ).toBe(false);
    expect(http.expectOne('/assets/logo.svg').request.headers.has('Authorization')).toBe(false);
  });

  it('sends no header when signed out', () => {
    setup(null);

    client.get('/api/groups').subscribe();

    expect(http.expectOne('/api/groups').request.headers.has('Authorization')).toBe(false);
  });

  it('ends the session and redirects to the login page on 401', () => {
    setup('jwt-1');
    let failed = false;

    client.get('/api/groups').subscribe({ error: () => (failed = true) });
    http.expectOne('/api/groups').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.endSession).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/groups?x=1' },
    });
  });

  it('leaves the session alone on other errors', () => {
    setup('jwt-1');

    client.get('/api/groups').subscribe({ error: () => undefined });
    http.expectOne('/api/groups').flush(null, { status: 500, statusText: 'Server Error' });

    expect(auth.endSession).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });
});
