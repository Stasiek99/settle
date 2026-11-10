import { DestroyRef, inject, signal, Signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription } from 'rxjs';

import { problemMessage } from './problem';

export interface Loader<T> {
  readonly value: Signal<T | null>;
  readonly loading: Signal<boolean>;
  readonly error: Signal<string | null>;
  load(): void;
}

export function createLoader<T>(fetch: () => Observable<T>): Loader<T> {
  const destroyRef = inject(DestroyRef);
  const value = signal<T | null>(null);
  const loading = signal(true);
  const error = signal<string | null>(null);
  let inFlight: Subscription | undefined;

  return {
    value: value.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    load(): void {
      inFlight?.unsubscribe();
      loading.set(true);
      error.set(null);
      inFlight = fetch()
        .pipe(takeUntilDestroyed(destroyRef))
        .subscribe({
          next: (result) => {
            value.set(result);
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
