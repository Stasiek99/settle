import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { AuthService } from '../core/auth/auth.service';
import { User } from '../core/models';

export const ANN: User = {
  id: 'u1',
  email: 'ann@example.com',
  displayName: 'Ann',
  createdAt: '',
};

export function setCsrfCookie(present: boolean): void {
  document.cookie = present
    ? 'XSRF-TOKEN=csrf-value; path=/'
    : 'XSRF-TOKEN=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
}

/** Makes the app think the browser already holds a valid session, like after a page reload. */
export function restoreSession(http: HttpTestingController, user: User = ANN): void {
  TestBed.inject(AuthService).restore().subscribe();
  http.expectOne('/api/users/me').flush(user);
}
