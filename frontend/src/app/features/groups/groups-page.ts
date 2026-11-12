import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
import { CURRENCIES } from '../../core/money';
import { createPager } from '../../core/pager';
import { problemMessage } from '../../core/problem';

@Component({
  selector: 'app-groups-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
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

  protected readonly currencies = CURRENCIES;
  protected readonly pager = createPager((page, size) => this.api.list(page, size));

  protected readonly dialogVisible = signal(false);
  protected readonly creating = signal(false);
  protected readonly createError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    currency: ['PLN', [Validators.required]],
  });

  constructor() {
    this.pager.load(0);
  }

  protected onPageChange(event: PaginatorState): void {
    this.pager.load(event.page ?? 0);
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
          this.pager.load(0);
        },
        error: (err: unknown) => {
          this.createError.set(problemMessage(err));
          this.creating.set(false);
        },
      });
  }
}
