import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, throwError } from 'rxjs';

import { createLoader } from './loader';

describe('createLoader', () => {
  it('starts loading with no value', () => {
    const loader = TestBed.runInInjectionContext(() => createLoader(() => new Subject<string>()));

    expect(loader.loading()).toBe(true);
    expect(loader.value()).toBeNull();
    expect(loader.error()).toBeNull();
  });

  it('exposes the loaded value', () => {
    const response = new Subject<string>();
    const loader = TestBed.runInInjectionContext(() => createLoader(() => response));

    loader.load();
    response.next('hello');

    expect(loader.value()).toBe('hello');
    expect(loader.loading()).toBe(false);
  });

  it('reports a failure', () => {
    const loader = TestBed.runInInjectionContext(() =>
      createLoader<string>(() => throwError(() => new HttpErrorResponse({ status: 0 }))),
    );

    loader.load();

    expect(loader.error()).toContain('Cannot reach the server');
    expect(loader.loading()).toBe(false);
  });

  it('keeps only the answer to the latest request', () => {
    const first = new Subject<string>();
    const second = new Subject<string>();
    const responses = [first, second];
    const loader = TestBed.runInInjectionContext(() => createLoader(() => responses.shift()!));

    loader.load();
    loader.load();
    second.next('latest');
    first.next('stale');

    expect(loader.value()).toBe('latest');
  });
});
