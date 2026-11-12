import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';

import { AuthService } from '../../core/auth/auth.service';
import { click, settle, text } from '../../testing/dom';
import { ANN, restoreSession, setCsrfCookie } from '../../testing/session';
import { Shell } from './shell';

describe('Shell', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    setCsrfCookie(true);
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

  afterEach(() => {
    http.verify();
    setCsrfCookie(false);
  });

  it('shows the name of the signed-in user without asking the server again', async () => {
    restoreSession(http);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await settle(fixture);

    http.expectNone('/api/users/me');
    expect(text(fixture)).toContain(ANN.displayName);
  });

  it('logs out and returns to the login page', async () => {
    restoreSession(http);
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await settle(fixture);

    click(fixture, 'p-button[label="Log out"] button');

    expect(http.expectOne('/api/auth/logout').request.method).toBe('POST');
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/login']);
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
  });
});
