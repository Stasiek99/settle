import { HttpErrorResponse } from '@angular/common/http';

import { problemMessage } from './problem';

function failure(status: number, body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ status, error: body });
}

describe('problemMessage', () => {
  it('uses the detail of a problem response', () => {
    expect(problemMessage(failure(409, { detail: 'A user with this email already exists' }))).toBe(
      'A user with this email already exists',
    );
  });

  it('lists field errors of a validation response', () => {
    const error = failure(400, {
      detail: 'Validation failed',
      errors: [
        { field: 'name', message: 'must not be blank' },
        { field: 'currency', message: 'must be a 3-letter currency code' },
      ],
    });

    expect(problemMessage(error)).toBe('name: must not be blank; currency: must be a 3-letter currency code');
  });

  it('explains a network failure', () => {
    expect(problemMessage(failure(0, null))).toContain('Cannot reach the server');
  });

  it('does not expose a server error body', () => {
    expect(problemMessage(failure(500, { detail: 'java.lang.NullPointerException at ...' }))).toContain(
      'server had a problem',
    );
  });

  it('falls back to a generic message', () => {
    expect(problemMessage(failure(418, null))).toBe('Something went wrong. Please try again.');
    expect(problemMessage(new Error('boom'))).toBe('Something went wrong. Please try again.');
  });
});
