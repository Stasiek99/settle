import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { providePrimeNG } from 'primeng/config';

import { routes } from '../../app.routes';
import { query, text } from '../../testing/dom';

const GROUP = {
  id: 'g1',
  name: 'Trip',
  currency: 'USD',
  createdBy: 'u1',
  createdAt: '2026-11-10T10:00:00Z',
  version: 0,
};
const MEMBERS = [
  { id: 'm1', userId: 'u1', displayName: 'Ann', role: 'OWNER', joinedAt: '' },
  { id: 'm2', userId: 'u2', displayName: 'Bob', role: 'MEMBER', joinedAt: '' },
];
const EMPTY_PAGE = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };

describe('group page routing', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    sessionStorage.setItem(
      'settle.session',
      JSON.stringify({ token: 'jwt', expiresAt: Date.now() + 3_600_000 }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes, withComponentInputBinding()),
        provideHttpClient(),
        provideHttpClientTesting(),
        providePrimeNG(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => sessionStorage.clear());

  async function flushAll(overrides: Record<string, unknown> = {}): Promise<void> {
    const answers: Record<string, unknown> = {
      '/api/users/me': { id: 'u1', email: 'ann@example.com', displayName: 'Ann', createdAt: '' },
      '/api/groups/g1': GROUP,
      '/api/groups/g1/members': MEMBERS,
      '/api/groups/g1/expenses': EMPTY_PAGE,
      '/api/groups/g1/balances': { currency: 'USD', balances: [] },
      '/api/groups/g1/settlements': EMPTY_PAGE,
      '/api/groups/g1/settlements/suggestions': { currency: 'USD', transfers: [] },
      ...overrides,
    };
    for (let round = 0; round < 3; round++) {
      for (const request of http.match(() => true)) {
        if (request.cancelled) {
          continue;
        }
        const answer = answers[request.request.url];
        if (answer instanceof Error) {
          request.flush({ detail: answer.message }, { status: 404, statusText: 'Not Found' });
        } else {
          request.flush(answer ?? null);
        }
      }
      await harness.fixture.whenStable();
      harness.detectChanges();
    }
  }

  it('opens the expenses tab of a group by default', async () => {
    await harness.navigateByUrl('/groups/g1');
    await flushAll();

    expect(TestBed.inject(Router).url).toBe('/groups/g1/expenses');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('Trip');
    expect(text(harness.fixture)).toContain('USD');
  });

  it('marks the open tab as the current page', async () => {
    await harness.navigateByUrl('/groups/g1/balances');
    await flushAll();

    const current = harness.routeNativeElement?.querySelector('nav a[aria-current="page"]');
    expect(current?.textContent).toContain('Balances');
    expect(harness.routeNativeElement?.querySelectorAll('nav a')).toHaveLength(3);
  });

  it('switches tabs with real links and loads that tab', async () => {
    await harness.navigateByUrl('/groups/g1');
    await flushAll();

    harness.routeNativeElement
      ?.querySelector<HTMLAnchorElement>('nav a[href="/groups/g1/settlements"]')
      ?.click();
    await flushAll();

    expect(TestBed.inject(Router).url).toBe('/groups/g1/settlements');
    expect(text(harness.fixture)).toContain('Suggested transfers');
  });

  it('offers a way back to the list of groups', async () => {
    await harness.navigateByUrl('/groups/g1');
    await flushAll();

    expect(query(harness.fixture, 'a[href="/groups"]')).not.toBeNull();
  });

  it('shows the problem and no tabs when the group cannot be opened', async () => {
    await harness.navigateByUrl('/groups/g1');
    await flushAll({ '/api/groups/g1': new Error('Group g1 not found') });

    expect(text(harness.fixture)).toContain('Group g1 not found');
    expect(harness.routeNativeElement?.querySelector('nav')).toBeNull();
  });
});
