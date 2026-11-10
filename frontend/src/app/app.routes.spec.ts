import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';

describe('routes', () => {
  function configure(): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
      ],
    });
  }

  function signIn(): void {
    sessionStorage.setItem(
      'settle.session',
      JSON.stringify({ token: 'jwt', expiresAt: Date.now() + 3_600_000 }),
    );
  }

  function answerPendingRequests(): void {
    const http = TestBed.inject(HttpTestingController);
    http
      .match('/api/users/me')
      .forEach((r) => r.flush({ id: '1', email: 'a@b.c', displayName: 'Ann', createdAt: '' }));
    http
      .match((r) => r.url === '/api/groups')
      .forEach((r) => r.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }));
  }

  beforeEach(() => sessionStorage.clear());

  it('shows the landing page to a guest at the root', async () => {
    configure();
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/');

    expect(TestBed.inject(Router).url).toBe('/');
    expect(harness.routeNativeElement?.querySelector('h1')?.getAttribute('aria-label')).toBe(
      'Settle',
    );
  });

  it('sends a signed-in user from the root to their groups', async () => {
    signIn();
    configure();
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/');
    answerPendingRequests();

    expect(TestBed.inject(Router).url).toBe('/groups');
  });

  it('sends a guest who asks for the groups to the login page and remembers the target', async () => {
    configure();
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/groups');

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fgroups');
  });

  it('sends unknown paths to the landing page for a guest', async () => {
    configure();
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/no/such/page');

    expect(TestBed.inject(Router).url).toBe('/');
  });
});
