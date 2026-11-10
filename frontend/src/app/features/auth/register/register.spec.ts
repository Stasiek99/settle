import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { setValue, settle, submit, text } from '../../../testing/dom';
import { Register } from './register';

describe('Register', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        providePrimeNG(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  function render() {
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    return fixture;
  }

  function fill(
    fixture: ReturnType<typeof render>,
    name: string,
    email: string,
    password: string,
  ): void {
    setValue(fixture, '#displayName', name);
    setValue(fixture, '#email', email);
    setValue(fixture, '#password', password);
  }

  it('validates the fields before calling the API', async () => {
    const fixture = render();
    fill(fixture, ' ', 'not-an-email', 'short');

    submit(fixture);
    await settle(fixture);

    http.expectNone('/api/auth/register');
    expect(text(fixture)).toContain('Enter a valid email address.');
    expect(text(fixture)).toContain('Use 8 to 72 characters.');
  });

  it('rejects a password longer than the 72 bytes bcrypt limit allows', () => {
    const fixture = render();
    fill(fixture, 'Ann', 'ann@example.com', 'x'.repeat(73));

    submit(fixture);

    http.expectNone('/api/auth/register');
  });

  it('creates the account, signs in and opens the groups page', async () => {
    const fixture = render();
    fill(fixture, ' Ann ', ' ann@example.com ', 'a-long-password');

    submit(fixture);
    const register = http.expectOne('/api/auth/register');
    expect(register.request.body).toEqual({
      displayName: 'Ann',
      email: 'ann@example.com',
      password: 'a-long-password',
    });
    register.flush(
      { id: '1', email: 'ann@example.com', displayName: 'Ann', createdAt: '' },
      { status: 201, statusText: 'Created' },
    );
    http
      .expectOne('/api/auth/login')
      .flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600 });
    http
      .expectOne('/api/users/me')
      .flush({ id: '1', email: 'ann@example.com', displayName: 'Ann', createdAt: '' });
    await settle(fixture);

    expect(navigateByUrl).toHaveBeenCalledWith('/groups');
  });

  it('shows the message when the email is already taken', async () => {
    const fixture = render();
    fill(fixture, 'Ann', 'ann@example.com', 'a-long-password');

    submit(fixture);
    http
      .expectOne('/api/auth/register')
      .flush(
        { detail: 'A user with this email already exists' },
        { status: 409, statusText: 'Conflict' },
      );
    await settle(fixture);

    expect(text(fixture)).toContain('A user with this email already exists');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
