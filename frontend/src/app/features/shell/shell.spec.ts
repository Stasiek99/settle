import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { AuthService } from '../../core/auth/auth.service';
import { click, settle, text } from '../../testing/dom';
import { Shell } from './shell';

describe('Shell', () => {
  let http: HttpTestingController;

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
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  it('loads the current user and shows their name', async () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    http
      .expectOne('/api/users/me')
      .flush({ id: '1', email: 'ann@example.com', displayName: 'Ann', createdAt: '' });
    await settle(fixture);

    expect(text(fixture)).toContain('Ann');
  });

  it('does not reload a user that is already known', () => {
    const auth = TestBed.inject(AuthService);
    auth.login('ann@example.com', 'x').subscribe();
    http
      .expectOne('/api/auth/login')
      .flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600 });
    http
      .expectOne('/api/users/me')
      .flush({ id: '1', email: 'ann@example.com', displayName: 'Ann', createdAt: '' });

    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();

    http.expectNone('/api/users/me');
  });

  it('logs out and returns to the login page', async () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    http
      .expectOne('/api/users/me')
      .flush({ id: '1', email: 'ann@example.com', displayName: 'Ann', createdAt: '' });
    await settle(fixture);

    click(fixture, 'p-button[label="Log out"] button');

    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login']);
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
  });
});
