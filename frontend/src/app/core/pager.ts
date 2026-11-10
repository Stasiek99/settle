import { DestroyRef, inject, signal, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription } from 'rxjs';

import { Page } from './models';
import { problemMessage } from './problem';

export const DEFAULT_PAGE_SIZE = 10;

export interface Pager<T> {
  readonly items: Signal<T[]>;
  readonly total: Signal<number>;
  readonly first: Signal<number>;
  readonly size: number;
  readonly loading: Signal<boolean>;
  readonly error: Signal<string | null>;
  load(page: number): void;
}

export function createPager<T>(
  fetch: (page: number, size: number) => Observable<Page<T>>,
  size = DEFAULT_PAGE_SIZE,
): Pager<T> {
  const destroyRef = inject(DestroyRef);
  const items = signal<T[]>([]);
  const total = signal(0);
  const first = signal(0);
  const loading = signal(true);
  const error = signal<string | null>(null);
  let inFlight: Subscription | undefined;

  return {
    items: items.asReadonly(),
    total: total.asReadonly(),
    first: first.asReadonly(),
    size,
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    load(page: number): void {
      inFlight?.unsubscribe();
      loading.set(true);
      error.set(null);
      inFlight = fetch(page, size)
        .pipe(takeUntilDestroyed(destroyRef))
        .subscribe({
          next: (result) => {
            items.set(result.content);
            total.set(result.totalElements);
            first.set(result.page * result.size);
            loading.set(false);
          },
          error: (err: unknown) => {
            error.set(problemMessage(err));
            loading.set(false);
          },
        });
    },
  };
}
