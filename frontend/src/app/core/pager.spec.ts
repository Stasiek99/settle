import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, throwError } from 'rxjs';

import { Page } from './models';
import { createPager } from './pager';

function page(items: string[], overrides: Partial<Page<string>> = {}): Page<string> {
  return {
    content: items,
    page: 0,
    size: 10,
    totalElements: items.length,
    totalPages: 1,
    ...overrides,
  };
}

describe('createPager', () => {
  it('starts in the loading state with nothing loaded', () => {
    const pager = TestBed.runInInjectionContext(() =>
      createPager(() => new Subject<Page<string>>()),
    );

    expect(pager.loading()).toBe(true);
    expect(pager.items()).toEqual([]);
    expect(pager.error()).toBeNull();
  });

  it('asks for the page with the configured size and exposes the result', () => {
    const fetch = vi.fn(() => new Subject<Page<string>>());
    const response = new Subject<Page<string>>();
    fetch.mockReturnValue(response);
    const pager = TestBed.runInInjectionContext(() => createPager(fetch, 5));

    pager.load(2);
    response.next(page(['a', 'b'], { page: 2, size: 5, totalElements: 12, totalPages: 3 }));

    expect(fetch).toHaveBeenCalledWith(2, 5);
    expect(pager.items()).toEqual(['a', 'b']);
    expect(pager.total()).toBe(12);
    expect(pager.first()).toBe(10);
    expect(pager.loading()).toBe(false);
  });

  it('reports a failure and clears the loading flag', () => {
    const pager = TestBed.runInInjectionContext(() =>
      createPager(() => throwError(() => new HttpErrorResponse({ status: 500 }))),
    );

    pager.load(0);

    expect(pager.error()).toContain('server had a problem');
    expect(pager.loading()).toBe(false);
  });

  it('clears an earlier error when loading again', () => {
    let fail = true;
    const pager = TestBed.runInInjectionContext(() =>
      createPager(() =>
        fail
          ? throwError(() => new HttpErrorResponse({ status: 500 }))
          : new Subject<Page<string>>(),
      ),
    );
    pager.load(0);
    fail = false;

    pager.load(0);

    expect(pager.error()).toBeNull();
    expect(pager.loading()).toBe(true);
  });

  it('ignores a slow response from a page that is no longer wanted', () => {
    const first = new Subject<Page<string>>();
    const second = new Subject<Page<string>>();
    const responses = [first, second];
    const pager = TestBed.runInInjectionContext(() => createPager(() => responses.shift()!));

    pager.load(0);
    pager.load(1);
    second.next(page(['newer'], { page: 1 }));
    first.next(page(['older']));

    expect(pager.items()).toEqual(['newer']);
    expect(pager.first()).toBe(10);
  });
});
