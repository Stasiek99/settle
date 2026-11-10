import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { AuthService } from './auth.service';
import { authGuard, guestGuard, safeReturnUrl } from './auth.guards';

describe('auth guards', () => {
  function run(
    guard: typeof authGuard,
    authenticated: boolean,
    url = '/groups',
  ): boolean | UrlTree {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: () => authenticated } },
      ],
    });
    return TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  it('authGuard lets a signed-in user through', () => {
    expect(run(authGuard, true)).toBe(true);
  });

  it('authGuard sends a guest to the login page and remembers where they wanted to go', () => {
    const result = run(authGuard, false, '/groups?page=2') as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe(
      '/login?returnUrl=%2Fgroups%3Fpage%3D2',
    );
  });

  it('guestGuard lets a guest see the login page', () => {
    expect(run(guestGuard, false)).toBe(true);
  });

  it('guestGuard sends a signed-in user to the groups', () => {
    const result = run(guestGuard, true) as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/groups');
  });
});

describe('safeReturnUrl', () => {
  it.each([
    ['/groups', '/groups'],
    ['/groups?page=2', '/groups?page=2'],
    [undefined, '/groups'],
    [null, '/groups'],
    ['', '/groups'],
    ['https://evil.example', '/groups'],
    ['//evil.example', '/groups'],
    ['/\\evil.example', '/groups'],
    ['javascript:alert(1)', '/groups'],
    ['groups', '/groups'],
  ])('%s -> %s', (candidate, expected) => {
    expect(safeReturnUrl(candidate)).toBe(expected);
  });
});
