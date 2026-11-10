import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DialogModule } from 'primeng/dialog';
import { FluidModule } from 'primeng/fluid';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';

import { GroupsApi } from '../../core/groups/groups.api';
import { Group } from '../../core/models';
import { problemMessage } from '../../core/problem';

export const PAGE_SIZE = 10;
export const CURRENCIES = ['PLN', 'EUR', 'USD', 'GBP', 'CHF', 'CZK'];

@Component({
  selector: 'app-groups-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    ButtonModule,
    CardModule,
    DialogModule,
    FluidModule,
    InputTextModule,
    MessageModule,
    PaginatorModule,
    SelectModule,
    SkeletonModule,
    TagModule,
  ],
  templateUrl: './groups-page.html',
  styleUrl: './groups-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GroupsPage {
  private readonly api = inject(GroupsApi);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly currencies = CURRENCIES;

  protected readonly groups = signal<Group[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly first = signal(0);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  protected readonly dialogVisible = signal(false);
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    currency: ['PLN', [Validators.required]],
  });

  constructor() {
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api
      .list(page, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.groups.set(result.content);
          this.totalElements.set(result.totalElements);
          this.first.set(result.page * result.size);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loadError.set(problemMessage(err));
          this.loading.set(false);
        },
      });
  }

  protected onPageChange(event: PaginatorState): void {
    this.load(event.page ?? 0);
  }

  protected openDialog(): void {
    this.form.reset({ name: '', currency: 'PLN' });
    this.createError.set(null);
    this.dialogVisible.set(true);
  }

  protected create(): void {
    if (this.form.invalid || this.creating()) {
      this.form.markAllAsTouched();
      return;
    }
    this.creating.set(true);
    this.createError.set(null);
    const { name, currency } = this.form.getRawValue();
    this.api
      .create(name.trim(), currency)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.creating.set(false);
          this.dialogVisible.set(false);
          this.load(0);
        },
        error: (err: unknown) => {
          this.createError.set(problemMessage(err));
          this.creating.set(false);
        },
      });
  }
}
