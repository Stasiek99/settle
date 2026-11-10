import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { query, setValue, settle, submit, text } from '../../../testing/dom';
import { Login } from './login';

describe('Login', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), providePrimeNG()],
    });
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  function render(returnUrl?: string) {
    const fixture = TestBed.createComponent(Login);
    if (returnUrl !== undefined) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    fixture.detectChanges();
    return fixture;
  }

  function fill(fixture: ReturnType<typeof render>, email: string, password: string): void {
    setValue(fixture, '#email', email);
    setValue(fixture, '#password', password);
  }

  function succeedLogin(): void {
    http.expectOne('/api/auth/login').flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600 });
    http.expectOne('/api/users/me').flush({ id: '1', email: 'a@b.c', displayName: 'Ann', createdAt: '' });
  }

  it('does not call the API and shows hints while the form is empty', async () => {
    const fixture = render();

    submit(fixture);
    await settle(fixture);

    http.expectNone('/api/auth/login');
    expect(text(fixture)).toContain('Email is required.');
    expect(text(fixture)).toContain('Password is required.');
  });

  it('signs in and goes to the groups page by default', async () => {
    const fixture = render();
    fill(fixture, ' ann@example.com ', 'secret-password');

    submit(fixture);
    succeedLogin();
    await settle(fixture);

    expect(navigateByUrl).toHaveBeenCalledWith('/groups');
  });

  it('trims the email but sends the password untouched', () => {
    const fixture = render();
    fill(fixture, ' ann@example.com ', ' pass word ');

    submit(fixture);

    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ email: 'ann@example.com', password: ' pass word ' });
    request.flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600 });
    http.expectOne('/api/users/me').flush({ id: '1', email: 'a@b.c', displayName: 'Ann', createdAt: '' });
  });

  it('returns to the page the user wanted', async () => {
    const fixture = render('/groups?page=2');
    fill(fixture, 'ann@example.com', 'secret-password');

    submit(fixture);
    succeedLogin();
    await settle(fixture);

    expect(navigateByUrl).toHaveBeenCalledWith('/groups?page=2');
  });

  it('ignores a return url that leaves the app', async () => {
    const fixture = render('https://evil.example/phish');
    fill(fixture, 'ann@example.com', 'secret-password');

    submit(fixture);
    succeedLogin();
    await settle(fixture);

    expect(navigateByUrl).toHaveBeenCalledWith('/groups');
  });

  it('shows the server message when the credentials are wrong and allows another try', async () => {
    const fixture = render();
    fill(fixture, 'ann@example.com', 'wrong-password');

    submit(fixture);
    http
      .expectOne('/api/auth/login')
      .flush({ detail: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });
    await settle(fixture);

    expect(text(fixture)).toContain('Invalid email or password');
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(query<HTMLButtonElement>(fixture, 'button[type=submit]')?.disabled).toBe(false);
  });

  it('does not send a second request while one is in flight', () => {
    const fixture = render();
    fill(fixture, 'ann@example.com', 'secret-password');

    submit(fixture);
    submit(fixture);

    http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
  });

  it('links to the registration page', () => {
    const fixture = render();

    expect(query<HTMLAnchorElement>(fixture, 'a[href="/register"]')).not.toBeNull();
  });
});
